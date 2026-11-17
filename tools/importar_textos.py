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

LIGACAO = {"a", "o", "as", "os", "da", "do", "das", "dos", "de", "e", "ou", "na", "no", "nas", "nos", "em", "com",
           "para", "por", "pela", "pelo", "pelas", "pelos", "ao", "aos", "à", "às", "um", "uma", "que", "se"}

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DADOS = os.path.join(RAIZ, "dados")
SAIDA = os.path.join(DADOS, "textos.json")
# quadro de cada ponto (título, incidência, caiu em, status, atenção): usado para criar os pontos
# de matérias que ainda não têm aba na planilha
SAIDA_QUADROS = os.path.join(DADOS, "quadros.json")

# Começo de dispositivo: força quebra de parágrafo
ESTRUTURA = re.compile(
    r"^(§|Art\.|Artigo\b|Parágrafo|Considerando\b|Histórico:|Redação original|Nº \d|SUM-|OJ-|Súmula\b|Tema\b|CAP[IÍ]TULO|Cap[ií]tulo|T[IÍ]TULO|Se[cç][aã]o|SE[CÇ][AÃ]O|Livro|LIVRO|"
    r"[IVXLC]+\s*[-–—.)]|\d+[.)º°]|\(?[a-z]\)|\(\w\)|[ivx]+\)|•)"
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
        anterior = (atual[-1] if atual else "").rstrip()
        # minúscula, ou número logo após linha sem pontuação final ("…de 14 de março de" + "1979")
        continuacao = (t[:1].islower() and not ESTRUTURA.match(t)) or (
            t[:1].isdigit() and anterior != "" and not anterior.endswith((".", ";", ":", "!", "?"))) or (
            # linha anterior termina em artigo/preposição: a frase continua ("…integrantes da" + "Seção, nos feitos…")
            anterior.rsplit(" ", 1)[-1].lower() in LIGACAO)
        quebra = not continuacao and (
            ultima is None
            or ultima["x1"] < margem - 60          # linha anterior terminou cedo
            or ultima["t"].endswith((":", ";"))
            or ESTRUTURA.match(t)
        )
        if quebra:
            fechar()
        if atual and atual[-1].endswith("-") and not atual[-1].endswith(" -"):
            # palavra hifenizada na quebra: "trin-" + "ta" = "trinta"; "Vice-" + "Presidente" mantém o hífen
            atual[-1] = (atual[-1][:-1] if t[:1].islower() else atual[-1]) + t
        else:
            atual.append(t)
        ultima = l
    fechar()
    return saida


def disciplina_do_bloco(texto, abas_para_chave):
    """'Processo do Trabalho – incidência Alta' -> chave da disciplina (ou None)."""
    nome = re.split(r"\s+[–-]\s+", texto)[0]
    return abas_para_chave.get(norm(nome))


def chave_natural(caminho):
    """Ordena 'parte2' antes de 'parte10'."""
    return [int(x) if x.isdigit() else x.lower() for x in re.split(r"(\d+)", os.path.basename(caminho))]


def processar(caminhos, abas_para_chave):
    """Processa os PDFs em sequência, como um documento só: um PDF dividido em partes
    (ex.: ..._parte1.pdf, ..._parte2.pdf) continua o ponto que ficou no meio."""
    todas = []
    for caminho in caminhos:
        ls = list(linhas(pdfplumber.open(caminho)))
        # capa: 2ª linha com o nome da aba (ex.: "Direitos Humanos") define a matéria
        titulo_aba = ls[1]["t"] if len(ls) > 1 and ls[0]["tam"] >= 16.0 else ""
        d = abas_para_chave.get(norm(titulo_aba)) or disciplina_do_bloco(titulo_aba, abas_para_chave)
        if titulo_aba:
            # capa de um novo vade mecum: encerra o ponto anterior (a matéria pode vir depois, nos blocos)
            todas.append({"t": titulo_aba, "tam": 99.0, "negrito": True, "capa": True, "disc": d, "x0": 0, "x1": 0})
        todas.extend(ls)
    disc_atual = None
    caminho = " + ".join(os.path.basename(c) for c in caminhos)

    incidencia = ""
    secoes = {}  # (disciplina, numero) -> {"titulo", "campos", "corpo"}
    atual = None
    campo = None
    margem = max([l["x1"] for l in todas if l["tam"] == 10.0] or [540])
    for l in todas:
        t = l["t"]
        if l.get("capa"):
            disc_atual = l["disc"]
            atual = None
            incidencia = ""
            continue
        if l["tam"] >= 16.0 and l["negrito"]:
            d = disciplina_do_bloco(t, abas_para_chave)
            if d:
                disc_atual = d
            if "incidencia" in norm(t) or "nao caiu" in norm(t):
                incidencia = t
            continue
        if l["tam"] <= 8.0 or l["tam"] > 16.0:
            continue  # rodapé e capa
        m = re.match(r"^(\d+)\. (.+)$", t)
        if l["negrito"] and l["tam"] == 13.0 and m:
            if not disc_atual:
                print(f"!! {caminho}: seção '{t}' sem matéria identificada", file=sys.stderr)
                atual = None
                continue
            n = int(m.group(1))
            atual = secoes.setdefault((disc_atual, n), {"titulo": m.group(2), "campos": {}, "corpo": [], "incidencia": incidencia})
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
            ant = atual["corpo"][-1] if atual["corpo"] else None
            if ant and ant[1].get("bloco_negrito") and ant[1]["x1"] >= margem - 60 and not ESTRUTURA.match(t):
                # continuação de título/caput em negrito que quebrou a linha
                ant[1]["t"] += " " + t
                ant[1]["x1"] = l["x1"]
                continue
            l["bloco_negrito"] = True
            # "Art. 129 - Todo empregado..." é caput (texto); os demais negritos são títulos
            atual["corpo"].append(("texto" if t.startswith("Art.") else "artigo", l))
        else:
            atual["corpo"].append(("texto", l))

    textos, conferencia, quadros = {}, {}, {}
    for (disc, n), s in sorted(secoes.items()):
        chave = f"{disc}-{n}"
        partes = []
        obs = s["campos"].get("Observação")
        if obs:
            partes.append("Observação: " + obs)
        partes += montar_paragrafos(s["corpo"])
        textos[chave] = "\n\n".join(partes).strip()
        conferencia[chave] = s["campos"].get("Na planilha", s["titulo"])
        quadros[chave] = {
            "disciplina": disc, "numero": n, "titulo": conferencia[chave], "incidencia": s["incidencia"],
            "caiuEm": s["campos"].get("Caiu em", ""), "vigencia": s["campos"].get("Status", ""),
            "atencao": s["campos"].get("Atenção", ""),
        }
    contagem = {}
    for disc, _ in secoes:
        contagem[disc] = contagem.get(disc, 0) + 1
    print(f"{caminho}: " + ", ".join(f"{d}: {c} pontos" for d, c in contagem.items()))
    return textos, conferencia, quadros


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
    arquivos = sorted(glob.glob(os.path.join(DADOS, "vade_mecum", "*.pdf")), key=chave_natural)
    if arquivos:
        t, conf, quadros = processar(arquivos, abas)
        with open(SAIDA_QUADROS, "w", encoding="utf-8") as f:
            json.dump(quadros, f, ensure_ascii=False, indent=1)
        for chave, na_planilha in conf.items():
            titulo = seed_titulos.get(chave)
            if titulo is None:
                print(f"  -- {chave}: sem aba na planilha (o ponto será criado a partir do PDF)")
            elif norm(titulo)[:25] != norm(na_planilha)[:25]:
                print(f"  ?? {chave}: planilha='{titulo[:60]}' / pdf='{na_planilha[:60]}'", file=sys.stderr)
        textos.update(t)
    with open(SAIDA, "w", encoding="utf-8") as f:
        json.dump(textos, f, ensure_ascii=False, indent=1)
    print(f"OK: {SAIDA} ({len(textos)} textos)")


if __name__ == "__main__":
    main()
