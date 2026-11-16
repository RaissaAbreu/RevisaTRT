#!/usr/bin/env python3
"""Gera app/src/main/assets/seed.json a partir de dados/edital.json e das planilhas em dados/*.xlsx.

Cada aba de planilha cujo nome coincide com o campo "planilha" (ou "nome") de uma disciplina
em edital.json vira a lista de pontos daquela disciplina. As colunas são lidas pelo cabeçalho.

Uso:  python3 tools/gerar_seed.py
Sempre que a base mudar, aumente SEED_VERSION para o app incorporar as novidades
sem apagar revisões, anotações ou favoritos.
"""
import glob
import json
import os
import re
import unicodedata

import openpyxl

SEED_VERSION = 2

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DADOS = os.path.join(RAIZ, "dados")
SAIDA = os.path.join(RAIZ, "app", "src", "main", "assets", "seed.json")

IMPORTANCIA = {
    "altissima": 4,
    "alta": 3,
    "media": 2,
    "baixa": 1,
    "nao caiu na amostra": 0,
}

# início do cabeçalho normalizado -> campo do ponto (o "(de N)" do cabeçalho vira o total da amostra)
COLUNAS = [
    ("#", "numero"),
    ("dispositivo", "titulo"),
    ("o que a fcc cobra", "oQueCai"),
    ("incidencia", "importancia"),
    ("cadernos", "cadernos"),
    ("questoes", "questoes"),
    ("area judiciaria", "areaJudiciaria"),
    ("oficial de justica", "oficialJustica"),
    ("status", "vigencia"),
    ("atencao", "atencao"),
    ("onde caiu", "ondeCaiu"),
    ("trf-3", "outrasProvas"),
    ("texto completo", "textoLei"),
    ("texto da lei", "textoLei"),
]


def campo_do_cabecalho(c):
    n = norm(c)
    for prefixo, campo in COLUNAS:
        if n == prefixo or (prefixo != "#" and n.startswith(prefixo)):
            m = re.search(r"\(de (\d+)\)", n)
            return campo, int(m.group(1)) if m else None
    return None, None


def norm(s):
    s = unicodedata.normalize("NFD", str(s or "")).encode("ascii", "ignore").decode()
    return " ".join(s.lower().split())


def texto(v):
    if v is None:
        return ""
    return str(v).strip()


def inteiro(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0


def ler_abas():
    abas = {}
    for caminho in sorted(glob.glob(os.path.join(DADOS, "*.xlsx"))):
        wb = openpyxl.load_workbook(caminho, data_only=True)
        for ws in wb.worksheets:
            abas[norm(ws.title)] = ws
    return abas


def pontos_da_aba(ws, chave_disc):
    linhas = [r for r in ws.iter_rows(values_only=True) if any(c is not None for c in r)]
    if not linhas:
        return []
    cab, totais = [], {}
    for c in linhas[0]:
        campo, total = campo_do_cabecalho(c)
        cab.append(campo)
        if campo and total:
            totais[campo] = total
    pontos = []
    for ordem, linha in enumerate(linhas[1:], start=1):
        bruto = {campo: linha[i] for i, campo in enumerate(cab) if campo and i < len(linha)}
        titulo = texto(bruto.get("titulo"))
        if not titulo:
            continue
        numero = inteiro(bruto.get("numero")) or ordem
        imp = IMPORTANCIA.get(norm(bruto.get("importancia")), 2)
        pontos.append({
            "chave": f"{chave_disc}-{numero}",
            "disciplina": chave_disc,
            "ordem": ordem,
            "titulo": titulo,
            "oQueCai": texto(bruto.get("oQueCai")),
            "importancia": imp,
            "cadernos": inteiro(bruto.get("cadernos")),
            "questoes": inteiro(bruto.get("questoes")),
            "areaJudiciaria": inteiro(bruto.get("areaJudiciaria")),
            "oficialJustica": inteiro(bruto.get("oficialJustica")),
            "vigencia": texto(bruto.get("vigencia")),
            "atencao": texto(bruto.get("atencao")),
            "ondeCaiu": texto(bruto.get("ondeCaiu")),
            "outrasProvas": "" if texto(bruto.get("outrasProvas")) in ("", "0") else texto(bruto.get("outrasProvas")),
            "textoLei": texto(bruto.get("textoLei")),
        })
    return pontos, totais


def main():
    with open(os.path.join(DADOS, "edital.json"), encoding="utf-8") as f:
        edital = json.load(f)
    abas = ler_abas()
    disciplinas, pontos = [], []
    for ordem, d in enumerate(edital["disciplinas"], start=1):
        disc = {
            "chave": d["chave"],
            "nome": d["nome"],
            "grupo": d["grupo"],
            "ordem": ordem,
            "edital": d["edital"],
            "totalCadernos": 0,
            "totalAJ": 0,
            "totalOJ": 0,
        }
        ws = abas.get(norm(d.get("planilha", d["nome"])))
        if ws is not None:
            novos, totais = pontos_da_aba(ws, d["chave"])
            pontos.extend(novos)
            disc["totalCadernos"] = totais.get("cadernos", 0)
            disc["totalAJ"] = totais.get("areaJudiciaria", 0)
            disc["totalOJ"] = totais.get("oficialJustica", 0)
            print(f"{d['nome']}: {len(novos)} pontos (amostra: {totais})")
        else:
            print(f"{d['nome']}: sem planilha ainda")
        disciplinas.append(disc)
    seed = {
        "versao": SEED_VERSION,
        "concurso": edital["concurso"],
        "dataProva": edital["dataProva"],
        "disciplinas": disciplinas,
        "pontos": pontos,
    }
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, "w", encoding="utf-8") as f:
        json.dump(seed, f, ensure_ascii=False, indent=1)
    print(f"OK: {SAIDA} ({len(pontos)} pontos)")


if __name__ == "__main__":
    main()
