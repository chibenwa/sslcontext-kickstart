/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.altindag.ssl.util;

import nl.altindag.ssl.exception.GenericIOException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Hakan Altindag
 */
public final class IOUtils {

    private IOUtils() {}

    public static String getContent(InputStream inputStream) {
        try (InputStreamReader inputStreamReader = new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            return bufferedReader.lines()
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception e) {
            throw new GenericIOException(e);
        }
    }

    static byte[] copyToByteArray(InputStream inputStream) {
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new GenericIOException(e);
        }
    }

    static void closeSilently(AutoCloseable autoCloseable) {
        try {
            autoCloseable.close();
        } catch (Exception ignored) {
            //ignore exception
        }
    }

}
