#!/usr/bin/env python3
"""Extrai o texto literal das leis dos PDFs "Vade mecum FCC TRT" (dados/vade_mecum/*.pdf)
e grava dados/textos.json  {chave_do_ponto: texto}.

Estrutura esperada do PDF (gerada junto com a planilha):
  - 2ª linha da 1ª página: nome da aba da planilha (ex.: "Direitos Humanos");
  - cada ponto começa com um título em negrito 13pt "N. Título", na mesma ordem (#) da aba;
  - campos do quadro em 9,5pt ("Na planilha:", "Caiu em:", "Status:", "Atenção:", "Observação:");
  - título de cada norma em negrito 10,5pt; títulos de artigo em negrito 10pt; texto em 10pt.

No texto gerado, "## " marca o título da norma e "### " o título do artigo (o app formata).

Uso:  python3 tools/importar_textos.py
"""
import glob
import json
import os
import re
import sys
import unicodedata

import pdfplumber

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DADOS = os.path.join(RAIZ, "dados")
SAIDA = os.path.join(DADOS, "textos.json")

# Começo de dispositivo: força quebra de parágrafo
ESTRUTURA = re.compile(
    r"^(§|Art\.|Artigo\b|Parágrafo|Considerando\b|CAP[IÍ]TULO|Cap[ií]tulo|T[IÍ]TULO|Se[cç][aã]o|SE[CÇ][AÃ]O|Livro|LIVRO|"
    r"[IVXLC]+\s*[-–—.)]|\d+[.)º°]|\(?[a-z]\)|\(\w\)|•|-\s)"
)
CAMPOS = ("Na planilha:", "Caiu em:", "Status:", "Atenção:", "Observação:")


def norm(s):
    s = unicodedata.normalize("NFD", str(s or "")).encode("ascii", "ignore").decode()
    return " ".join(s.lower().split())


def linhas(pdf):
    for p in pdf.pages:
        for l in p.extract_text_lines(return_chars=True):
            c = l["chars"][0]
            letras = [ch for ch in l["chars"] if ch["text"].strip()]
            frac_negrito = sum("Bold" in ch["fontname"] for ch in letras) / max(len(letras), 1)
            yield {
                "t": l["text"].strip(),
                "tam": round(c["size"], 1),
                # linha de título = quase toda em negrito ("Art. 1º" em negrito + caput normal é texto)
                "negrito": frac_negrito > 0.6,
                "inicio_negrito": "Bold" in c["fontname"],
                "x0": l["x0"],
                "x1": l["x1"],
            }


def montar_paragrafos(corpo):
    """corpo: lista de (tipo, linha). tipo: 'norma', 'artigo', 'texto'."""
    saida, atual, ultima = [], [], None
    margem = max([l["x1"] for t, l in corpo if t == "texto"] or [540])

    def fechar():
        nonlocal atual
        if atual:
            saida.append(" ".join(atual))
            atual = []

    for tipo, l in corpo:
        t = l["t"]
        if tipo == "norma":
            fechar(); saida.append("## " + t); ultima = None; continue
        if tipo == "artigo":
            fechar(); saida.append("### " + t); ultima = None; continue
        quebra = (
            ultima is None
            or ultima["x1"] < margem - 60          # linha anterior terminou cedo
            or ultima["t"].endswith((":", ";"))
            or ESTRUTURA.match(t)
        )
        if quebra:
            fechar()
        if atual and atual[-1].endswith("-") and not atual[-1].endswith(" -"):
            atual[-1] = atual[-1][:-1] + t  # palavra hifenizada na quebra
        else:
            atual.append(t)
        ultima = l
    fechar()
    return saida


def processar(caminho, abas_para_chave):
    pdf = pdfplumber.open(caminho)
    todas = list(linhas(pdf))
    titulo_aba = todas[1]["t"] if len(todas) > 1 else ""
    chave_disc = abas_para_chave.get(norm(titulo_aba))
    if not chave_disc:
        print(f"!! {os.path.basename(caminho)}: aba '{titulo_aba}' não corresponde a nenhuma disciplina", file=sys.stderr)
        return {}, {}

    secoes = {}  # numero -> {"planilha": str, "obs": str, "corpo": []}
    atual = None
    campo = None
    for l in todas:
        t = l["t"]
        if l["tam"] <= 8.0 or l["tam"] >= 16.0:
            continue  # rodapé e "Incidência ..."
        m = re.match(r"^(\d+)\. (.+)$", t)
        if l["negrito"] and l["tam"] == 13.0 and m:
            n = int(m.group(1))
            atual = secoes.setdefault(n, {"titulo": m.group(2), "campos": {}, "corpo": []})
            campo = None
            continue
        if atual is None:
            continue
        if l["tam"] == 9.5:
            rot = next((c for c in CAMPOS if t.startswith(c)), None)
            if rot and l["inicio_negrito"]:
                campo = rot[:-1]
                atual["campos"][campo] = t[len(rot):].strip()
            elif campo:
                atual["campos"][campo] += " " + t
            continue
        campo = None
        if l["negrito"] and l["tam"] >= 10.5:
            atual["corpo"].append(("norma", l))
        elif l["negrito"]:
            atual["corpo"].append(("artigo", l))
        else:
            atual["corpo"].append(("texto", l))

    textos, conferencia = {}, {}
    for n, s in sorted(secoes.items()):
        chave = f"{chave_disc}-{n}"
        partes = []
        obs = s["campos"].get("Observação")
        if obs:
            partes.append("Observação: " + obs)
        partes += montar_paragrafos(s["corpo"])
        textos[chave] = "\n\n".join(partes).strip()
        conferencia[chave] = s["campos"].get("Na planilha", s["titulo"])
    print(f"{os.path.basename(caminho)}: {len(textos)} pontos de '{titulo_aba}' -> {chave_disc}")
    return textos, conferencia


def main():
    with open(os.path.join(DADOS, "edital.json"), encoding="utf-8") as f:
        edital = json.load(f)
    abas = {}
    for d in edital["disciplinas"]:
        abas[norm(d.get("planilha", d["nome"]))] = d["chave"]
        abas[norm(d["nome"])] = d["chave"]

    seed_titulos = {}
    seed = os.path.join(RAIZ, "app", "src", "main", "assets", "seed.json")
    if os.path.exists(seed):
        seed_titulos = {p["chave"]: p["titulo"] for p in json.load(open(seed, encoding="utf-8"))["pontos"]}

    textos = {}
    for caminho in sorted(glob.glob(os.path.join(DADOS, "vade_mecum", "*.pdf"))):
        t, conf = processar(caminho, abas)
        for chave, na_planilha in conf.items():
            titulo = seed_titulos.get(chave)
            if titulo is None:
                print(f"  !! {chave}: ponto não existe na planilha", file=sys.stderr)
            elif norm(titulo)[:25] != norm(na_planilha)[:25]:
                print(f"  ?? {chave}: planilha='{titulo[:60]}' / pdf='{na_planilha[:60]}'", file=sys.stderr)
        textos.update(t)
    with open(SAIDA, "w", encoding="utf-8") as f:
        json.dump(textos, f, ensure_ascii=False, indent=1)
    print(f"OK: {SAIDA} ({len(textos)} textos)")


if __name__ == "__main__":
    main()
