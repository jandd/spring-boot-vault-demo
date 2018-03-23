# Spring Boot vault demo

## Getting started

Create an empty `bootstrap.yml`:

```
touch bootstrap.yml
```

Run gradle to build all necessary files:

```
./gradlew build
```

### Setup vault

Start the vault container:

```
docker-compose up --build vault
```

Initialize Vault:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  vault operator init -key-shares=1 -key-threshold=1 > vault_data.txt
```

Keep the information you will need the root token and unseal key later.

Unseal Vault:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  vault operator unseal <unseal key from vault_data.txt>
```

Check the root token to see if Vault is working:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  -e VAULT_TOKEN=<token from vault_data.txt> \
  vault token lookup
```

### Run the spring-boot application

```
docker-compose up --build web
```