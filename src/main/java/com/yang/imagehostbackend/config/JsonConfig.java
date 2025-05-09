package com.yang.imagehostbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * 配置 Jackson 的 ObjectMapper,将 Long/long 类型序列化为字符串,来避免 Long 类型在 JSON 序列化时丢失精度
 * JavaScript 的 Number 类型最大安全整数是 2^53 - 1。
 * Java 的 Long 类型最大值是 2^63 - 1。
 * 当 Java 返回的 Long 值超过 JavaScript 的安全范围时，前端会丢失精度（如 1234567890123456789 可能变成 1234567890123456800）。
 * @Author 小小星仔
 * @Create 2025-05-09 20:07
 */
@JsonComponent
public class JsonConfig {
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
