# CVForge

CVForge é uma aplicação web para criar e importar currículos, editar seus dados, exportar PDFs e comparar um perfil profissional com uma vaga.

## Funcionalidades

- Criar, editar, importar e organizar vários currículos.
- Importar currículos em PDF ou JSON e editar os dados como JSON.
- Visualizar e exportar currículos em seis layouts: ATS, executivo, terminal, moderno, criativo e compacto.
- Comparar um currículo ou perfil com a descrição de uma vaga e salvar o resultado com pontuação, empresa, currículo usado e data.
- Gerar uma prévia em PDF do currículo ajustado para uma vaga. A prévia fica como rascunho; os dados e metadados do currículo ajustado só são salvos depois da confirmação do usuário.
- Usar a interface em português ou inglês.

## Repositórios

O Gitea é a origem canônica do projeto:

- Gitea: [leo/cvforge](https://gitea.jaquesprojetos.com.br/leo/cvforge)
- Cópia pública no GitHub: [LeonardoJaques/cvforge](https://github.com/LeonardoJaques/cvforge)

Atualize primeiro o branch `main` no Gitea e sincronize o GitHub a partir dele.

## Arquitetura

- **Frontend:** Angular 18, TypeScript e SCSS.
- **API:** Java 21, Spring Boot 3.4, Spring Security e Spring Data JPA.
- **Banco:** PostgreSQL 16, com migrações Flyway.
- **Análise de vagas:** OpenRouter, chamado somente pelo backend.
- **Execução:** Docker Compose e Nginx; em produção, Traefik e autenticação centralizada.

A autenticação é delegada a um proxy confiável, que envia o cabeçalho `Remote-User`. A API só confia nesse cabeçalho quando a conexão vem do endereço configurado em `CVFORGE_TRUSTED_PROXY_IP`. Não exponha a API diretamente à Internet nem aceite esse cabeçalho de clientes sem autenticação.

Os currículos e as comparações são armazenados no PostgreSQL configurado pelo operador. Quando a análise por IA é usada, o backend envia à OpenRouter o conteúdo necessário para essa análise.

## Configuração e execução local

1. Copie `.env.example` para `.env`.
2. Configure uma senha forte para `POSTGRES_PASSWORD` e um segredo aleatório para `JWT_SECRET`.
3. Para habilitar a análise de vagas, configure `OPENROUTER_API_KEY`.
4. Ajuste `LLDAP_HTTP_URL`, `LLDAP_LDAP_BASE_DN` e `CVFORGE_TRUSTED_PROXY_IP` ao seu ambiente.
5. Configure o proxy para autenticar o usuário, encaminhar `Remote-User` e remover o prefixo `/cvforge` antes de encaminhar as requisições.

Com Docker e Docker Compose:

```sh
docker compose up --build -d
```

O Compose de exemplo espera uma rede Docker externa chamada `web`. Adapte rede, portas e regras do proxy antes de publicar. As configurações específicas do VPS não fazem parte do Compose local. A API oferece saúde em `/api/v1/health`.

Para validar os serviços separadamente, use Java 21 e Maven no backend e Node.js 20 no frontend. Os Dockerfiles executam os testes durante o build.

## Deploy no servidor

O job manual `cvforge-deploy` do Jenkins busca o branch `main` do Gitea e implanta o commit obtido nessa execução. Ele valida o Compose e o banco, compila e testa API e frontend, atualiza somente esses dois serviços e verifica a saúde. Se o rollout falhar, tenta restaurar as imagens anteriores. O serviço de banco não é reiniciado pelo job.

## Licença

Este projeto está licenciado sob a **PolyForm Noncommercial License 1.0.0**. Uso, modificação e redistribuição são permitidos para fins não comerciais, conforme os termos em [LICENSE](LICENSE). Uso comercial exige autorização prévia e por escrito do mantenedor.

---

## English

CVForge is a web application for creating and importing resumes, editing resume data, exporting PDFs, and comparing a profile with a job description.

It supports six PDF layouts, Portuguese and English, saved job comparisons, and tailored-resume previews. A tailored draft is persisted only after the user confirms the save. The frontend uses Angular 18; the API uses Java 21 and Spring Boot 3.4; PostgreSQL stores resumes and comparisons; OpenRouter is called by the backend for job analysis.

**Gitea is the canonical source.** Update its `main` branch first, then synchronize the public copy at [LeonardoJaques/cvforge](https://github.com/LeonardoJaques/cvforge).

Copy `.env.example` to `.env`, replace all placeholders, configure a trusted authenticated reverse proxy that sets `Remote-User`, and run `docker compose up --build -d`. The sample Compose file expects an external Docker network named `web`. Do not expose the API directly to the Internet. The API health endpoint is `/api/v1/health`.

The manual Jenkins job `cvforge-deploy` checks out the Gitea `main` branch and deploys the exact commit used by that build. It validates Compose and the database, builds and tests the API and frontend, updates only those two services, checks health, and attempts rollback on failure. It does not restart PostgreSQL.

This project is licensed under the **PolyForm Noncommercial License 1.0.0**. Commercial use requires prior written permission from the maintainer.
