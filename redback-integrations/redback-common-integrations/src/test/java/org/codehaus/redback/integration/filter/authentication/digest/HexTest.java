package org.codehaus.redback.integration.filter.authentication.digest;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;

import org.codehaus.plexus.digest.Hex;

public class HexTest
    extends TestCase
{
    public void testEncoding()
    {
        String raw = "Lenore\nLenore";
        String lenoreHex = "4c656e6f7265";
        String expected = lenoreHex + "0a" + lenoreHex;

        assertEquals( expected, Hex.encode( raw ) );
    }

    public void testTheRaven()
    {
        String raw = "Quoth the Raven, \"Nevermore.\"";
        String expected = "51756f74682074686520526176656e2c20224e657665726d6f72652e22";

        assertEquals( expected, Hex.encode( raw.getBytes() ) );
    }
}
