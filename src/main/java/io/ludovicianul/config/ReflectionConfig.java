package io.ludovicianul.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {org.sqlite.JDBC.class})
public class ReflectionConfig {}
