# Como publicar esta wiki no GitHub

A wiki de um repositório GitHub é, na verdade, um repositório git separado: `wonder-app.wiki.git`. Ele só existe depois que pelo menos uma página é criada pela interface web pelo menos uma vez.

## Passo a passo

1. No repositório, acesse a aba **Wiki** e clique em **Create the first page** (crie qualquer conteúdo provisório e salve — isso inicializa o repositório da wiki).
2. Clone o repositório da wiki localmente:
   ```bash
   git clone https://github.com/llwkascarvalho/wonder-app.wiki.git
   ```
3. Copie todos os arquivos desta pasta (`*.md` e a pasta `images/`) para dentro do clone, substituindo o conteúdo provisório.
4. Commit e push:
   ```bash
   cd wonder-app.wiki
   git add .
   git commit -m "docs: adiciona wiki completa (requisitos, visões arquiteturais, C4, modelagem e operação)"
   git push
   ```
5. Recarregue a aba Wiki no GitHub — o `_Sidebar.md` vira automaticamente o menu lateral, e `Home.md` vira a página inicial.

## Conteúdo incluído

- `Home.md` — página inicial e navegação
- `Requisitos.md`
- `Processo-de-Software.md`
- `Visao-Funcional.md`
- `Visao-de-Desenvolvimento.md`
- `C4-Contexto.md`
- `C4-Container.md`
- `C4-Componentes.md`
- `Casos-de-Uso.md`
- `Diagrama-de-Classes.md`
- `Modelo-Logico.md`
- `Auditoria-Monitoramento-e-Backup.md`
- `_Sidebar.md` — menu lateral
- `images/` — os 8 diagramas em PNG referenciados pelas páginas

## Observações

- Os nomes dos arquivos usam hífen em vez de espaço e sem acento, seguindo a convenção de URL da wiki do GitHub — os links entre páginas já usam esses nomes.
- Marquei com "Nota de atualização (verificada em código)" os pontos em que o código no commit `e687be7` já foi além do que os diagramas mostram (principalmente a nova dependência AI → Catálogo). Revise essas notas e, se concordar, atualize os diagramas de origem antes do próximo commit da wiki.
- O RF011 duplicado no documento de requisitos original foi mantido como está na página Requisitos, com uma nota sugerindo renomear para RF013 na próxima revisão.
