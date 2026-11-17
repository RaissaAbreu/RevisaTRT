#!/usr/bin/env python3
"""Converte PDFs oficiais de normas (dados/normas/*.pdf) em texto do ponto correspondente.

Diferente do vade mecum, aqui cada arquivo é uma norma inteira, ligada a um ponto pela tabela NORMAS.
Gera dados/textos_normas.json {chave_do_ponto: texto}; o gerar_seed.py dá preferência a esse texto.

Uso:  python3 tools/importar_normas.py
"""
import collections
import json
import os
import re
import sys

import pdfplumber

LIGACAO = {"a", "o", "as", "os", "da", "do", "das", "dos", "de", "e", "ou", "na", "no", "nas", "nos", "em", "com",
           "para", "por", "pela", "pelo", "pelas", "pelos", "ao", "aos", "à", "às", "um", "uma", "que", "se"}

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importar_textos import ESTRUTURA  # noqa: E402

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DADOS = os.path.join(RAIZ, "dados")
SAIDA = os.path.join(DADOS, "textos_normas.json")

# arquivo -> ponto, título exibido, aviso no topo, onde começa o texto (regex da 1ª linha a manter)
NORMAS = {
    "regimento_interno_trt4.pdf": {
        "chave": "legislacao-1",
        "titulo": "Regimento Interno do TRT da 4ª Região",
        "aviso": "Texto oficial enviado por você, atualizado até a RA nº 41, de 29/10/2026 (Assento Regimental nº 06/2026). "
                 "O edital fixa a versão atualizada até a RA nº 31, de 17/08/2026: as alterações das RAs nº 32 a 41 "
                 "aparecem no texto com a indicação do Assento que as fez — em caso de dúvida na prova, vale a versão do edital.",
        "inicio": r"^T[IÍ]TULO I$",
        "ocorrencia_inicio": 2,  # a 1ª é a do sumário
    },
    "codigo_conduta_trt4.pdf": {
        "chave": "legislacao-2",
        "titulo": "Código de Conduta e Valores Éticos do TRT4 (Portaria GP.TRT4 nº 4.573/2023, compilada com a Portaria GP.TRT4 nº 2.808/2025)",
        "aviso": "Texto oficial compilado enviado por você.",
    },
    "resolucao_csjt_360_2023.pdf": {
        "chave": "legislacao-4",
        "titulo": "Resolução CSJT nº 360/2023 (republicada por força da Resolução CSJT nº 455/2026)",
        "aviso": "Texto oficial enviado por você (republicação no DEJT de 08/09/2026).",
    },
}


def linhas(pdf):
    for p in pdf.pages:
        altura = p.height
        for l in p.extract_text_lines(return_chars=True):
            letras = [c for c in l["chars"] if c["text"].strip()]
            if not letras:
                continue
            yield {
                "t": l["text"].strip(),
                "tam": round(collections.Counter(round(c["size"], 1) for c in letras).most_common(1)[0][0], 1),
                "negrito": sum("Bold" in c["fontname"] for c in letras) / len(letras) > 0.8,
                "x0": l["x0"], "x1": l["x1"],
                # faixa de cabeçalho/rodapé da página
                "margem_pag": l["top"] < altura * 0.13 or l["top"] > altura * 0.9,
            }


def converter(caminho, cfg):
    todas = list(linhas(pdfplumber.open(caminho)))
    corpo_tam = collections.Counter(l["tam"] for l in todas).most_common(1)[0][0]
    # cabeçalhos repetidos em várias páginas (ex.: "PODER JUDICIÁRIO") e rodapés/números de página
    # (só no topo/rodapé da página: finais de linha como "11/2008)" também se repetem no meio do texto)
    repetidas = {t for t, n in collections.Counter(l["t"] for l in todas if l["margem_pag"]).items() if n >= 4}
    todas = [
        l for l in todas
        if l["tam"] >= corpo_tam - 1.5
        and not (l["margem_pag"] and (l["t"] in repetidas or re.fullmatch(r"\d+", l["t"])))
    ]

    if cfg.get("inicio"):
        achadas = [i for i, l in enumerate(todas) if re.match(cfg["inicio"], l["t"])]
        n = cfg.get("ocorrencia_inicio", 1)
        if len(achadas) >= n:
            todas = todas[achadas[n - 1]:]

    texto_corpo = [l for l in todas if not l["negrito"]]
    esquerda = collections.Counter(round(l["x0"]) for l in texto_corpo).most_common(1)[0][0]
    direita = sorted(l["x1"] for l in texto_corpo)[int(len(texto_corpo) * 0.9)]

    paragrafos, atual, ultima = [], [], None

    def fechar():
        nonlocal atual
        if atual:
            paragrafos.append(" ".join(atual))
            atual = []

    for l in todas:
        t = l["t"]
        centralizada = l["x0"] > esquerda + 60 and l["x1"] < direita - 60
        titulo = l["negrito"] and (centralizada or len(t) < 90)
        if titulo:
            # título de várias linhas (ex.: nome do capítulo quebrado)
            if paragrafos and paragrafos[-1].startswith("### ") and ultima is not None and ultima.get("titulo") \
                    and not re.match(r"^(T[IÍ]TULO|CAP[IÍ]TULO|SE[CÇ][AÃ]O|SUBSE[CÇ][AÃ]O|ANEXO)\\b", t, re.I) \
                    and t[:1].islower():
                paragrafos[-1] += " " + t
            else:
                fechar()
                paragrafos.append("### " + t)
            ultima = dict(l, titulo=True)
            continue
        recuo = l["x0"] > esquerda + 30  # 1ª linha de parágrafo recuada
        anterior = (atual[-1] if atual else "").rstrip()
        # minúscula, ou número logo após linha sem pontuação final ("…de 14 de março de" + "1979")
        continuacao = (t[:1].islower() and not ESTRUTURA.match(t)) or (
            t[:1].isdigit() and anterior != "" and not anterior.endswith((".", ";", ":", "!", "?"))) or (
            # linha anterior termina em artigo/preposição: a frase continua ("…integrantes da" + "Seção, nos feitos…")
            anterior.rsplit(" ", 1)[-1].lower() in LIGACAO)
        quebra = not continuacao and (
            ultima is None or ultima.get("titulo") or ultima["x1"] < direita - 45 or recuo or ESTRUTURA.match(t)
        )
        if quebra:
            fechar()
        if atual and atual[-1].endswith("-") and not atual[-1].endswith(" -"):
            # "trin-" + "ta" = "trinta"; "Vice-" + "Presidente" = "Vice-Presidente"
            atual[-1] = (atual[-1][:-1] if t[:1].islower() else atual[-1]) + t
        else:
            atual.append(t)
        ultima = l
    fechar()

    cabecalho = ["Observação: " + cfg["aviso"], "## " + cfg["titulo"]]
    return "\n\n".join(cabecalho + paragrafos)


def main():
    saida = {}
    for arquivo, cfg in NORMAS.items():
        caminho = os.path.join(DADOS, "normas", arquivo)
        if not os.path.exists(caminho):
            print(f"-- {arquivo}: não encontrado")
            continue
        texto = converter(caminho, cfg)
        saida[cfg["chave"]] = texto
        print(f"{arquivo} -> {cfg['chave']}: {len(texto)} caracteres, {texto.count(chr(10) * 2) + 1} parágrafos")
    with open(SAIDA, "w", encoding="utf-8") as f:
        json.dump(saida, f, ensure_ascii=False, indent=1)
    print(f"OK: {SAIDA}")


if __name__ == "__main__":
    main()
