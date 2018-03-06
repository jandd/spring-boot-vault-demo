# Spring Boot vault demo

## Getting started

### Setup vault

Create a private key and X.509 server certificate for Vault.

```
mkdir -p vault/config/ssl/private
openssl req -config vault/openssl.cnf -new -x509 -out vault/config/ssl/vault.crt.pem
```

Build and run `docker-compose` to setup vault and *Spring Boot* application containers:

```
./gradlew build && docker-compose up --build
```

Initialize Vault:

```
docker exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem springbootvaultdemo_vault_1 \
  vault operator init -key-shares=1 -key-threshold=1 > vault_data.txt
```

Keep the information you will need the root token and unseal key later.

Unseal Vault:

```
docker exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem springbootvaultdemo_vault_1 \
  vault operator unseal <unseal key from vault_data.txt>
```

Check the root token to see if Vault is working:

```
docker exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem \
  -e VAULT_TOKEN=<token from vault_data.txt> \
  vault token lookup
```