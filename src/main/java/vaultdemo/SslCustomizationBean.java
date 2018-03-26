/*
 * Copyright 2018 Jan Dittberner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vaultdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

@Component
public class SslCustomizationBean implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
    private VaultTemplate vaultTemplate;

    public SslCustomizationBean(@Autowired VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        factory.setSslStoreProvider(new SslStoreProvider() {
            @Override
            public KeyStore getKeyStore() {
                VaultCertificateResponse vaultResponse = vaultTemplate.opsForPki().issueCertificate(
                        "server", VaultCertificateRequest.create("localhost"));
                return vaultResponse.getData().createKeyStore("server");
            }

            @Override
            public KeyStore getTrustStore() throws Exception {
                Certificate cert = vaultTemplate.doWithVault(c -> c.execute("pki/ca", HttpMethod.GET, request -> {
                }, response -> {
                    try {
                        return CertificateFactory.getInstance("X.509").generateCertificate(response.getBody());
                    } catch (CertificateException e) {
                        throw new RuntimeException("Error reading CA certificate from vault", e);
                    }
                }));
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null, null);
                trustStore.setCertificateEntry("vault", cert);
                return trustStore;
            }
        });
    }
}
