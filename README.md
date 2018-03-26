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

> To run the spring-boot application from within an IDE you need to copy `build/resources/main/vault-truststore.jks`
> to `src/main/resources` so that it is on the classpath.

On the first attempt running the application will fail with:

> `java.lang.IllegalArgumentException: Token (spring.cloud.vault.token) must not be empty`

### Create a new token for your application

Use vault to create a new application token:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  -e VAULT_TOKEN=<token from vault_data.txt> \
  vault token create -policy=default
```

Put the following block into `bootstrap.yml`:

```yaml
spring.cloud.vault:
  token: <token-from-the-vault-output>
```

> To run the spring-boot application from an IDE you will need to add another line:
>
> ```yaml
>   host: localhost
> ```
>
> take care of the indentation (2 spaces)

Now try again:

```
./gradlew build && docker-compose up --build web
```

Now the application starts, but we get the following log messages (lines wrapped for readability):

> ```
> [RequestedSecret [path='secret/hello-vault', mode=ROTATE]]
>   Lease [leaseId='null', leaseDuration=PT0S, renewable=false]
>   Status 403 secret/hello-vault: permission denied
> [RequestedSecret [path='secret/application', mode=ROTATE]]
>   Lease [leaseId='null', leaseDuration=PT0S, renewable=false] 
>   Status 403 secret/application: permission denied
> ```

This is caused by Vaults default policy ACLs that do not allow access to the `secret` path.

Add a new policy to allow access for the application, use [Vault's API]() to do this (docker-compose cannot access local
files) that are not shared with the vault container in advance):

```
curl -H "X-Vault-Token: <root token from vault_data.txt>" \
  --cacert vault/config/ssl/vault.crt.pem \
  --request PUT --data @./vault/hello-application.json \
  https://localhost:8200/api/v1/sys/policy/hello-application 
```

Create a new token for the application:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  -e VAULT_TOKEN=<token from vault_data.txt> \
  vault token create -policy=hello-application
```

Put the new token in `bootstrap.yml` and restart the application:

```
docker-compose stop web
./gradlew build
docker-compose up --build web
```

Now the application starts without errors.
