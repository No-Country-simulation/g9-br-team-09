# EnergIAI - Backend Java

Este diretório contém a API REST do projeto **EnergIAI**, desenvolvida em Java com Spring Boot. A API é responsável pelos cálculos de custo de energia, validação das requisições e persistência dos dados de análises.

---

## 🛠️ Tecnologias e Dependências

O projeto utiliza as seguintes tecnologias e bibliotecas:

- **Java 21**
- **Spring Boot 4.0.7**
- **Spring Boot Starter Web & Validation** (para endpoints HTTP e validação de payloads)
- **Spring Boot Starter Data JPA** (persistência e mapeamento objeto-relacional)
- **Spring Boot Starter Flyway** (gerenciamento e evolução do esquema do banco de dados)
- **Springdoc OpenAPI (Swagger)** (geração automática de documentação da API)
- **H2 Database** (banco de dados em memória para testes e desenvolvimento local)
- **Oracle JDBC Driver (OJDBC11) & Flyway Oracle** (para integração com o Oracle Autonomous Database na OCI)
- **Lombok** (redução de código boilerplate)

---

## 📂 Estrutura de Pacotes

A estrutura principal do código Java está organizada da seguinte forma:

```text
src/main/java/br/com/g9/energiai/backend/
├── dto/
│   ├── request/        # Modelos de entrada para as requisições da API
│   └── response/       # Modelos de saída (respostas estruturadas)
├── enums/              # Enums de domínio (PropertyType, ClassificationSource, EnergyCategory)
├── service/            # Regras de negócio e componentes de serviço (ex: EnergyCostCalculator)
└── BackendApplication.java # Classe principal de inicialização do Spring Boot
```

---

## ⚙️ Configuração de Ambientes e Perfis

O projeto gerencia as configurações por meio de perfis do Spring (`profiles`):

1. **`application.properties`**: Configurações globais de negócio, como a tarifa padrão de energia:
   ```properties
   energy.tariff.default=0.75
   ```
2. **`application-local.properties`**: Configuração padrão para desenvolvimento local usando banco de dados **H2 em memória**:
   ```properties
   spring.datasource.url=jdbc:h2:mem:energiai
   spring.datasource.driver-class-name=org.h2.Driver
   spring.datasource.username=sa
   spring.datasource.password=
   ```
3. **`application-oci.properties`**: Configurações específicas para o ambiente de produção na **Oracle Cloud Infrastructure (OCI)** (deve ser preenchido com as credenciais do Oracle Autonomous Database e a Wallet/Connection String).

---

## 🚀 Como Executar

### Pré-requisitos
- **Java JDK 21** ou superior instalado.
- **Maven** (opcional, o wrapper `mvnw` já está incluso no repositório).

### Rodando Localmente (Perfil H2)
Para executar a aplicação localmente utilizando o banco em memória:

```bash
# No diretório 'backend'
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A aplicação subirá na porta padrão **8080**.

### Executando em Produção (Perfil OCI)
Após preencher as propriedades do banco de dados no arquivo `application-oci.properties`, execute:

```bash
# No diretório 'backend'
./mvnw spring-boot:run -Dspring-boot.run.profiles=oci
```

---

## 🩺 Documentação da API (Swagger / OpenAPI)

Com a aplicação rodando, a documentação interativa do Swagger pode ser acessada em:

- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

### Endpoint Principal

- **`POST /analise-energetica`**
  - **Descrição**: Recebe os dados de consumo do imóvel e retorna a análise de eficiência e custos.
  - **Payload de Entrada (JSON)**:
    ```json
    {
      "consumoKwh": 420.0,
      "usoHorarioPico": true,
      "quantidadeEquipamentos": 10,
      "tipoImovel": "CASA",
      "horasAltoConsumo": 8
    }
    ```

---

## 🧪 Testes

Para executar os testes unitários do backend:

```bash
./mvnw test
```
