package br.com.locadora.locacoes;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TarifaProperties.class)
public class LocacaoConfiguration {}
