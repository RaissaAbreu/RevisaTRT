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
import unicodedata

import openpyxl

SEED_VERSION = 1

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

# cabeçalho normalizado -> campo do ponto
COLUNAS = {
    "#": "numero",
    "dispositivo": "titulo",
    "o que a fcc cobra": "oQueCai",
    "incidencia": "importancia",
    "cadernos (de 25)": "cadernos",
    "questoes": "questoes",
    "area judiciaria (de 15)": "areaJudiciaria",
    "oficial de justica (de 10)": "oficialJustica",
    "status": "vigencia",
    "atencao: vigencia e pegadinhas": "atencao",
    "onde caiu": "ondeCaiu",
    "texto completo": "textoLei",
    "texto da lei": "textoLei",
}


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
    cab = [COLUNAS.get(norm(c)) for c in linhas[0]]
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
            "textoLei": texto(bruto.get("textoLei")),
        })
    return pontos


def main():
    with open(os.path.join(DADOS, "edital.json"), encoding="utf-8") as f:
        edital = json.load(f)
    abas = ler_abas()
    disciplinas, pontos = [], []
    for ordem, d in enumerate(edital["disciplinas"], start=1):
        disciplinas.append({
            "chave": d["chave"],
            "nome": d["nome"],
            "grupo": d["grupo"],
            "ordem": ordem,
            "edital": d["edital"],
        })
        ws = abas.get(norm(d.get("planilha", d["nome"])))
        if ws is not None:
            novos = pontos_da_aba(ws, d["chave"])
            pontos.extend(novos)
            print(f"{d['nome']}: {len(novos)} pontos")
        else:
            print(f"{d['nome']}: sem planilha ainda")
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
