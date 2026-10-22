# CVForge

Aplicação para criar e importar currículos, visualizar e exportar PDF, editar os dados em JSON e comparar um perfil profissional com uma vaga.

## Stack

- Frontend: Angular 18, TypeScript, RxJS e SCSS
- Backend: Java 21, Spring Boot 3.4 e Spring Data JPA
- Dados: PostgreSQL e Flyway
- Análise de vagas: OpenRouter
- Execução: Docker Compose, com Nginx e Traefik no servidor

## Configuração

Copie `.env.example` para `.env` e preencha as configurações de banco de dados, autenticação e OpenRouter. Não envie o arquivo `.env` ao Git.

Para executar localmente com Docker Compose:

```sh
docker compose up --build
```

O arquivo `docker-compose.server.yml` contém a configuração usada no servidor, incluindo as rotas do Traefik.
