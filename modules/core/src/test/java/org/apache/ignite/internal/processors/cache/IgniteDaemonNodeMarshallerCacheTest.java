/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.junits.common.*;

import java.io.*;

import static org.apache.ignite.cache.CacheMode.*;
import static org.apache.ignite.cache.CacheRebalanceMode.*;
import static org.apache.ignite.internal.IgniteNodeAttributes.*;

/**
 *
 */
public class IgniteDaemonNodeMarshallerCacheTest extends GridCommonAbstractTest {
    /** */
    private static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    private boolean daemon;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setDaemon(daemon);

        ((TcpDiscoverySpi)cfg.getDiscoverySpi()).setIpFinder(ipFinder);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testMarshalOnDaemonNode1() throws Exception {
        marshalOnDaemonNode(true);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMarshalOnDaemonNode2() throws Exception {
        marshalOnDaemonNode(false);
    }

    /**
     * @param startFirst If {@code true} daemon node is started first.
     * @throws Exception If failed.
     */
    public void marshalOnDaemonNode(boolean startFirst) throws Exception {
        int nodeIdx = 0;

        if (!startFirst) {
            Ignite ignite1 = startGrid(nodeIdx++);

            assertFalse("true".equals(ignite1.cluster().localNode().attribute(ATTR_DAEMON)));
        }

        daemon = true;

        Ignite daemonNode = startGrid(nodeIdx++);

        assertEquals("true", daemonNode.cluster().localNode().attribute(ATTR_DAEMON));

        daemon = false;

        if (startFirst) {
            Ignite ignite1 = startGrid(nodeIdx++);

            assertFalse("true".equals(ignite1.cluster().localNode().attribute(ATTR_DAEMON)));
        }

        awaitPartitionMapExchange();

        TestClass1 res1 = daemonNode.compute(daemonNode.cluster().forRemotes()).call(new TestCallable1());

        assertNotNull(res1);
        assertEquals(111, res1.val);

        Ignite ignite2 = startGrid(nodeIdx);

        CacheConfiguration<Object, Object> ccfg = new CacheConfiguration<>();

        ccfg.setRebalanceMode(SYNC);
        ccfg.setCacheMode(REPLICATED);

        IgniteCache<Object, Object> cache = ignite2.getOrCreateCache(ccfg);

        awaitPartitionMapExchange();

        cache.put(1, new TestClass1(1));
        cache.put(2, new TestClass2(2));

        TestClass2 res2 = daemonNode.compute(daemonNode.cluster().forRemotes()).call(new TestCallable2());

        assertNotNull(res2);
        assertEquals(222, res2.val);
    }

    /**
     *
     */
    private static class TestCallable1 implements IgniteCallable<TestClass1> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public TestClass1 call() throws Exception {
            assertFalse("true".equals(ignite.cluster().localNode().attribute(ATTR_DAEMON)));

            return new TestClass1(111);
        }
    }

    /**
     *
     */
    private static class TestCallable2 implements IgniteCallable<TestClass2> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public TestClass2 call() throws Exception {
            assertFalse("true".equals(ignite.cluster().localNode().attribute(ATTR_DAEMON)));

            return new TestClass2(222);
        }
    }

    /**
     *
     */
    private static class TestClass1 implements Serializable {
        /** */
        public int val;

        /**
         * @param val Value.
         */
        public TestClass1(int val) {
            this.val = val;
        }
    }

    /**
     *
     */
    private static class TestClass2 implements Serializable {
        /** */
        public int val;

        /**
         * @param val Value.
         */
        public TestClass2(int val) {
            this.val = val;
        }
    }
}
