package com.ject.oogachaka.global.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Spring 애플리케이션과 DB를 시작하지 않고 설정값 한 줄을 암호화한다.
 */
public final class JasyptEncryptCommand {

    private JasyptEncryptCommand() {
    }

    public static void main(String[] args) throws Exception {
        var encryptor = JasyptEncryptorFactory.createFromEnvironment();
        String value;
        if (System.console() != null) {
            char[] input = System.console().readPassword("암호화할 값: ");
            value = input == null ? null : new String(input);
            if (input != null) {
                java.util.Arrays.fill(input, '\0');
            }
        } else {
            value = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("암호화할 값 한 줄을 입력하세요.");
        }
        System.out.println("ENC(" + encryptor.encrypt(value) + ")");
    }
}
