path "secret/*" {
  capabilities = [
    "read",
    "list"]
}

path "pki/issue/server" {
  capabilities = [
    "update"]
}

path "pki/ca*" {
  capabilities = [
    "read"]
}

path "database/creds/springdemo" {
  capabilities = [
    "read"]
}

path "sys/revoke/database/creds/springdemo/*" {
  capabilities = [
  ]
}