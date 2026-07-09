.DEFAULT_GOAL := help

.PHONY: help contexto send git-context

# ========================= Help =========================

help: ## Mostra os comandos disponíveis
	@echo "Comandos disponíveis:"
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
	awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

# ========================= Contexto =========================

contexto: ## Gera o arquivo contexto.txt com repomix
	@echo "Limpando vestígios anteriores..."
	@rm -f contexto.txt
	@echo "Gerando novo contexto..."
	@npx --yes repomix --output contexto.txt

send: contexto ## Copia o contexto para o clipboard e apaga o arquivo
	@echo "Copiando para o clipboard..."
	@if command -v xclip > /dev/null; then \
		cat contexto.txt | xclip -selection clipboard; \
		echo "Copiado para o clipboard!"; \
	else \
		echo "Erro: xclip não encontrado."; \
	fi
	@echo "Limpando arquivo de contexto para manter o diretório limpo..."
	@rm -f contexto.txt
	@echo "Pronto! O arquivo contexto.txt foi removido."

git-context: ## Gera contexto Git + Prompt AI para o clipboard
	@echo "### AI INSTRUCTIONS ###" > context.tmp
	@cat docs/ai/commit-prompt.txt >> context.tmp
	@echo "\n### GIT STATUS ###" >> context.tmp
	@git status --porcelain=1 -b >> context.tmp
	@echo "\n### GIT NAME-STATUS ###" >> context.tmp
	@git diff --cached --name-status >> context.tmp
	@echo "\n### GIT STAT ###" >> context.tmp
	@git diff --cached --stat >> context.tmp
	@echo "\n### GIT DIFF ###" >> context.tmp
	@git diff --cached >> context.tmp
	@echo "\n### GIT LOG ###" >> context.tmp
	@git log --oneline -n 20 >> context.tmp
	@cat context.tmp | xclip -selection clipboard
	@rm context.tmp
	@echo "▐▓▒░ Contexto e Instruções copiados com sucesso!"
