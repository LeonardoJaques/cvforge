# Auditoria de ranks, empresas e conteúdo do CVForge

Auditoria do checkout local da aplicação usada em `jaquesprojetos.com.br/cvforge`.
O objetivo deste arquivo é registrar lacunas verificáveis e servir de checklist
para revisão das correções. Nenhum código da aplicação foi alterado nesta auditoria.

## Atualização: comparações salvas

A API usa objetos JSON do Jackson 2, mas o Spring Boot 4 podia serializá-los com
outro conversor. Isso impedia a interface de receber corretamente campos já salvos,
como `matchScore` e `companyName`. A configuração da API usa agora o conversor
compatível; o cartão preserva a nota e a empresa existentes e indica ausência de
nota sem fabricar um valor. Para uma empresa ausente, a interface só tenta obter
um nome quando o texto da vaga contém um marcador explícito.

## Escopo e limite dos dados

- O repositório não contém currículo de exemplo, catálogo ou fixtures com uma lista
  canônica de empresas e cargos. Portanto, não é possível apontar uma empresa
  específica ausente dos dados reais de usuários sem inventar conteúdo.
- Nenhum conjunto real de currículos ou comparações de usuários foi acessado nesta
  auditoria. As conclusões vêm somente dos modelos, formulários, rótulos e fluxos
  presentes no código. Nomes próprios de empresas devem continuar vindo da vaga ou
  do usuário; não devem ser fabricados por um catálogo de demonstração.
- No início da auditoria, o checkout estava limpo. Os agentes de correção já estavam
  ativos em paralelo; os itens abaixo são uma linha de base e devem ser conferidos
  contra as alterações deles antes de marcar qualquer item como pendente.

## Achados

### 1. O rank da compatibilidade não tem contrato fechado no código — ambiguidade

`JobMatchResult.level` é uma `string` livre no frontend. O prompt do backend solicita
somente `level (string)`, e o normalizador copia o valor retornado pela IA sem mapear
faixas para a pontuação. A tela imprime esse valor ao lado do score. Isso demonstra
que não há uma taxonomia determinística documentada no código, mas não prova por si só
que os rótulos atuais estejam incorretos: o significado pretendido pode ser avaliação
qualitativa de compatibilidade. O exemplo de teste usa “Sênior”, que também pode
significar senioridade profissional, então há ambiguidade semântica a resolver.

Com resposta sem `matchScore`, a implementação obtém `-1` e o limita a `0`; com
`level` ausente, exibe string vazia. Esse caminho para respostas incompletas é
demonstrável no código, embora não tenha sido reproduzido com uma resposta real da IA.

**Evidência:**

- `frontend/src/app/models/resume.model.ts` — `JobMatchResult.level: string`.
- `backend/src/main/java/com/cvforge/application/service/JobMatchApplicationService.java`
  — prompt de análise e `parseObject`, que copia `level` diretamente.
- `frontend/src/app/components/dashboard/dashboard.component.html` — o resultado
  mostra `analysis.matchScore` seguido por `analysis.level`.
- `backend/src/test/java/com/cvforge/service/JobMatchServiceOutputLimitTest.java`
  — o exemplo de resposta usa `"level":"Sênior"`, que pode denotar compatibilidade
  ou senioridade profissional.

**Decisão de produto:** esclarecer o significado de `level`. Se for rank de
compatibilidade, definir faixas/limites e rótulos pt/en. Se for texto qualitativo,
documentar esse contrato e não apresentá-lo como nível profissional do candidato.
Revisar também como respostas incompletas devem aparecer, sem transformar score
ausente em um zero aparentemente válido.

### 2. Busca de biblioteca não incluía empresa nem cargo — corrigido em paralelo

O placeholder da biblioteca promete busca por cargo, empresa ou título. A função
`filtered` procura somente no título do currículo, nome e headline. Empresas e cargos
registrados em `experience` não participam da busca.

**Evidência:**

- `frontend/src/app/services/i18n.service.ts` — `searchPlaceholder` é
  `Cargo, empresa ou título…` / `Role, company, or title…`.
- `frontend/src/app/components/dashboard/dashboard.component.ts` — `filtered`
  testa apenas `item.title`, nome e headline.
- `frontend/src/app/models/resume.model.ts` — cada experiência tem `role` e `company`.

**Correção observada durante a auditoria:** a alteração paralela em
`frontend/src/app/components/dashboard/dashboard.component.ts` agora inclui cargos e
empresas de todas as experiências na busca e normaliza acentos e caixa. O item está
atendido no diff atual; a revisão final deve confirmar que essa mudança permanece
após a integração.

### 3. Nome da empresa é metadado da comparação salva, não da análise — decisão de produto

O formulário coleta `companyName`, mas `analyzeJob()` não envia esse campo ao
backend. Ao salvar, a aplicação associa ao resultado o nome informado ou tenta
extraí-lo da descrição; se ambos estiverem ausentes, o cartão mostra “Empresa não
informada”. Portanto, o comportamento observado é que o nome serve como metadado do
histórico, não como parte da análise. A gravação após salvar está implementada; não há
evidência de perda do nome nesse fluxo. Só é uma lacuna se o produto espera mostrar
esse nome junto ao resultado antes de salvar.

**Evidência:**

- `frontend/src/app/components/dashboard/dashboard.component.html` — campo
  `jobCompany` e cartão que usa `result.companyName` ou o fallback.
- `frontend/src/app/components/dashboard/dashboard.component.ts` — `analyzeJob`
  cria a requisição sem `companyName`; `saveComparison` adiciona o campo e chama
  `extractCompanyName` apenas quando salva.
- `frontend/src/app/services/i18n.service.ts` — rótulos “Nome da empresa” e
  “Empresa não informada”.
- `backend/src/main/java/com/cvforge/application/service/JobMatchApplicationService.java`
  — o esquema de saída do prompt não inclui `companyName`.

**Decisão de produto:** confirmar se empresa precisa estar visível antes de salvar.
Se a extração automática for mantida, validar que ela não substitui a entrada manual
e que o fallback “Empresa não informada” é aceitável. Não declarar nomes inferidos
como certos sem evidência. Nenhuma amostra real de vagas foi usada para avaliar a
precisão dos padrões de extração.

### 4. Nome de empresa em experiência é texto livre — decisão de produto / limite de dados

O editor grava `company` como texto simples com limite de caracteres. A importação
aceita aliases em JSON e extrai texto de PDF/perfil; na normalização, campos ausentes
viram string vazia. O código não mantém catálogo de empresas, aliases ou sugestão de
grafia. Sem currículos reais, não é possível afirmar que existam nomes específicos
faltando ou que a grafia entre usuários esteja inconsistente.

**Evidência:**

- `frontend/src/app/components/editor/editor.component.html` e `.ts` — campo de
  empresa em texto livre, sem validação específica ou sugestão.
- `frontend/src/app/services/resume.service.ts` — normalização mantém `company` como
  string vazia quando ausente.
- `backend/src/main/java/com/cvforge/adapter/out/file/ResumeImportAdapter.java` —
  importação de JSON mapeia aliases; importação de texto/PDF depende da estrutura
  extraída.

**Decisão de produto:** confirmar se é desejado adicionar orientação/sugestão ou aviso
quando importação deixa empresa vazia. Manter o nome original fiel ao documento; não
normalizar nomes legais, abreviações ou subsidiárias automaticamente sem confirmação.

### 5. Cargo e senioridade não são campos separados — decisão de produto

No currículo, `ExperienceItem` guarda `role` como uma única string (por exemplo,
“Engenheira Sênior” ou “Staff Engineer”). A experiência não contém um campo próprio
de nível/rank. Isso é flexível para títulos reais, mas impede filtros e comparação
consistentes por senioridade sem interpretar o texto.

**Evidência:**

- `frontend/src/app/models/resume.model.ts` — `ExperienceItem` contém apenas
  `role`, `company`, `period` e `description`.
- `frontend/src/app/components/editor/editor.component.html` — uma entrada de
  texto para cargo; não há campo de nível.
- `backend/src/main/java/com/cvforge/application/service/ProfileTextImportApplicationService.java`
  — o schema de importação solicita `role`, sem senioridade independente.

**Revisão esperada:** decidir se “rank” se refere a senioridade do cargo ou à
compatibilidade com a vaga. Se senioridade for necessária como dado separado, ela
deve ser opcional e não substituir o título original; preservar cargos que não se
encaixam numa hierarquia simples (por exemplo, pesquisador, especialista ou
contribuidor individual).

## Checklist para a revisão das correções paralelas

- [ ] Significado de `level` está definido; se representar rank de compatibilidade,
      as faixas e traduções seguem um contrato único. Se for qualitativo, não se
      confunde com senioridade profissional.
- [ ] Comportamento para resposta da IA sem score ou sem `level` é intencional e não
      mascara dado ausente.
- [x] Busca da biblioteca encontra cargo e empresa em qualquer experiência, além do
      título do currículo e headline prometidos (correção vista no diff paralelo;
      confirmar após integração).
- [ ] Nome da empresa digitado permanece como metadado do histórico salvo; confirmar
      se a UX precisa exibi-lo também antes de salvar.
- [ ] Empresas desconhecidas/ausentes continuam representadas corretamente, sem
      preenchimento inventado.
- [ ] Se houver novo campo de senioridade, importação, edição, JSON e preview mantêm
      compatibilidade com currículos antigos sem rank.
- [ ] Exemplos adicionados usam dados claramente identificados como fictícios e não
      implicam endosso ou experiência profissional real.
