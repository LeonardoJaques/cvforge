# CVForge

**CVForge** é uma aplicação web para criar e importar currículos, editar seus dados, gerar PDFs e comparar um perfil profissional com uma vaga de emprego usando IA.

## Funcionalidades

- Criar, editar, importar e organizar vários currículos.
- Importar currículos em PDF ou JSON e editar os dados diretamente em JSON.
- Visualizar e exportar o currículo em PDF.
- Comparar um currículo ou perfil profissional com a descrição de uma vaga.
- Salvar comparações com pontuação, empresa, currículo usado e data.
- Interface em português e inglês.

## Arquitetura

- **Frontend:** Angular 18, TypeScript e SCSS.
- **API:** Java 21, Spring Boot 3.4, Spring Security e Spring Data JPA.
- **Banco:** PostgreSQL 16, com migrações Flyway.
- **IA:** OpenRouter, chamado exclusivamente pelo backend.
- **Containers:** Docker Compose e Nginx.

O navegador chama a API no mesmo prefixo de URL da aplicação. A instalação de produção espera um proxy reverso que encaminhe a interface e a API e remova o prefixo /cvforge antes de encaminhar as requisições.

### Autenticação e segurança

A autenticação é delegada a um proxy confiável. O proxy deve definir o cabeçalho Remote-User, e a API só confia nele quando a conexão vem do endereço configurado em CVFORGE_TRUSTED_PROXY_IP. **Não exponha a API diretamente à Internet** nem aceite esse cabeçalho de clientes sem autenticação.

A lista de origens permitidas para operações de escrita está definida em RemoteUserAuthenticationFilter; ajuste-a para o domínio da sua instalação. Guarde credenciais e chaves somente no backend, em variáveis de ambiente.

## Configuração

1. Copie .env.example para .env.
2. Defina uma senha forte para POSTGRES_PASSWORD e um segredo aleatório para JWT_SECRET.
3. Para habilitar a análise de vagas, configure OPENROUTER_API_KEY. A chave é usada somente pela API.
4. Configure LLDAP_HTTP_URL, LLDAP_LDAP_BASE_DN e CVFORGE_TRUSTED_PROXY_IP de acordo com seu ambiente.
5. Ajuste a origem permitida em RemoteUserAuthenticationFilter e configure o proxy para encaminhar Remote-User após autenticar o usuário.

O docker-compose.yml de exemplo espera que exista uma rede Docker externa chamada web, compartilhada com o proxy reverso. Adapte redes, portas e regras do proxy antes de publicar uma instalação. As configurações específicas do servidor de produção não fazem parte deste repositório.

## Executar

Com Docker e Docker Compose instalados, preencha .env, conecte os serviços à rede do proxy e execute:

~~~sh
docker compose up --build -d
~~~

As migrações do banco são aplicadas pela API na inicialização. O endpoint de saúde é GET /api/v1/health/status.

Para executar os serviços separadamente, use Java 21 e Maven no backend e Node.js 20 no frontend. Consulte backend/pom.xml e frontend/package.json para dependências e comandos.

## Privacidade

Os currículos e as comparações ficam no PostgreSQL configurado pelo operador. Quando a análise por IA é usada, o backend envia à OpenRouter o conteúdo necessário para a análise. Revise as políticas do provedor e informe os usuários da sua instalação.

## English

CVForge is a web application for creating and importing resumes, editing resume data, generating PDFs, and comparing a professional profile with a job description using AI.

It uses Angular 18 and TypeScript, a Java 21 / Spring Boot API, PostgreSQL with Flyway migrations, and OpenRouter for job analysis. OpenRouter is called by the backend only. Resume data and saved comparisons are stored in the configured PostgreSQL database.

Copy .env.example to .env, replace all placeholders, configure a trusted reverse proxy that authenticates users and sets Remote-User, then run docker compose up --build -d. Do not expose the API directly to the Internet. The sample Compose file expects an existing external Docker network named web; adapt proxy routes, trusted proxy IP, and allowed browser origin for your environment.

## Licença

Nenhuma licença foi definida ainda. Consulte o mantenedor antes de reutilizar ou redistribuir o código.
