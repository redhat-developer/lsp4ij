/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.extractors;

import com.redhat.devtools.lsp4ij.dap.configurations.extractors.DynamicSegment.DynamicSegmentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the address / port from a given String input by defining
 * a pattern which uses ${address} and ${port}.
 *
 * <p>
 * For the given pattern:
 * <p>
 * "DAP server listening at: ${address}:${port}"
 * <p>
 * and the given input:
 * <p>
 * DAP server listening at: 127.0.0.1:61537
 * <p>
 * The extracted address will be 127.0.0.1 and the extracted port will be 61537
 * </p>
 */
public class NetworkAddressExtractor {

    private static final ExtractorResult NO_MATCHES = new ExtractorResult(false, null, null);

    private final static Map<String, DynamicSegment> DYNAMIC_SEGMENTS;

    static {
        DYNAMIC_SEGMENTS = new HashMap<>();
        registerDynamicSegment(new AddressSegment(), DYNAMIC_SEGMENTS);
        registerDynamicSegment(new PortSegment(), DYNAMIC_SEGMENTS);
    }

    private final List<Segment> segments; // Liste contenant à la fois les segments statiques et dynamiques

    /**
     * Extractor constructor.
     *
     * @param pattern which uses ${address} and ${port} (ex: "DAP server listening at: ${address}:${port}")
     */
    public NetworkAddressExtractor(String pattern) {
        this.segments = new ArrayList<>();
        boolean dynamicPatternScanning = false;
        StringBuilder currentSegment = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '$' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '{') {
                // starts with '${'
                i++;
                dynamicPatternScanning = true;
                if (currentSegment.length() > 0) {
                    segments.add(new StaticSegment(currentSegment.toString()));
                }
                currentSegment.setLength(0);
                currentSegment.append(c);
                currentSegment.append(pattern.charAt(i));
            } else {
                currentSegment.append(c);
                if (dynamicPatternScanning) {
                    if (c == '}') {
                        String dynamicSegmentName = currentSegment.toString();
                        DynamicSegment dynamicSegment = DYNAMIC_SEGMENTS.get(dynamicSegmentName);
                        if (dynamicSegment != null) {
                            segments.add(dynamicSegment);
                            currentSegment.setLength(0);
                        }

                        dynamicPatternScanning = false;
                    }
                }
            }
        }
        if (currentSegment.length() > 0) {
            segments.add(new StaticSegment(currentSegment.toString()));
        }
    }

    /**
     * Extractor address / port from the given String input.
     *
     * @param input the real DAP server trace (ex: DAP server listening at: 127.0.0.1:61537)
     * @return the extractor result (ex:address=127.0.0.1, port=61537)
     */
    @NotNull
    public ExtractorResult extract(@Nullable String input) {
        if (input == null) {
            return NO_MATCHES;
        }
        int currentIndex = 0;
        String address = null;
        String port = null;
        for (Segment segment : segments) {
            String value = segment.matches(input);
            if (value == null) {
                return NO_MATCHES;
            }
            if (segment.isDynamic()) {
                DynamicSegmentType type = ((DynamicSegment) segment).getType();
                switch (type) {
                    case ADDRESS:
                        address = value;
                        break;
                    case PORT:
                        port = value;
                        break;
                }
            }
            currentIndex = value.length();
            input = input.substring(currentIndex);

        }
        return new ExtractorResult(true, address, port);
    }

    public List<Segment> getSegments() {
        return segments;
    }

    private static void registerDynamicSegment(DynamicSegment segment, Map<String, DynamicSegment> dynamicSegments) {
        dynamicSegments.put(segment.getValue(), segment);
    }

}
