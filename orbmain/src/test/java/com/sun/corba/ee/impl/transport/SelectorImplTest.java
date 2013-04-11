package com.sun.corba.ee.impl.transport;
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
import com.sun.corba.ee.spi.threadpool.Work;
import com.sun.corba.ee.spi.transport.Acceptor;
import com.sun.corba.ee.spi.transport.Connection;
import org.glassfish.simplestub.SimpleStub;
import org.glassfish.simplestub.Stub;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectorImplTest extends TransportTestBase {

    private TimerFake timer = new TimerFake();
    private SelectorImpl selector = new SelectorImpl(getOrb(), timer);
    private EventHandlerFake eventHandler = new EventHandlerFake();
    private NioSelectorFake selectorStub = Stub.create(NioSelectorFake.class);
    private SelectableChannelFake channel = Stub.create(SelectableChannelFake.class);

    @Before
    public void setUpTest() throws Exception {
        simulateStartSelectorCall();
        eventHandler.channel = channel;
    }

    private void simulateStartSelectorCall() {
        setPrivateFieldValue(selector, "selector", selectorStub);
        setPrivateFieldValue(selector, "selectorStarted", Boolean.TRUE);
    }

    @Test
    public void whenRegisteringAcceptEventWithoutSelectThread_listenerThreadIsCreated() {
        eventHandler.setUseSelectThreadToWait(false);
        eventHandler.setInterestOps(SelectionKey.OP_ACCEPT);
        selector.registerForEvent(eventHandler);
        assertTrue(getWorkQueue().remove() instanceof ListenerThreadImpl);
    }

    @Test
    public void whenRegisteringReadEventWithoutSelectThread_readerThreadIsCreated() {
        eventHandler.setUseSelectThreadToWait(false);
        eventHandler.setInterestOps(SelectionKey.OP_READ);
        selector.registerForEvent(eventHandler);
        assertTrue(getWorkQueue().remove() instanceof ReaderThreadImpl);
    }

    @Test
    public void whenSelectorClosed_nioSelectorClosedAndLoopEnds() throws IOException {
        selector.close();

        selector.runSelectionLoopOnce();
        assertFalse(selectorStub.isOpen());
    }

    @Test
    public void whenRegisteringEventWithSelectThread_wakeUpSelector() throws IOException {
        registerEventHandler();

        assertTrue(selectorStub.wakeupCalled);
    }

    @Test
    public void afterRegisteringEventWithSelectThread_runRegistersChannel() throws IOException {
        registerEventHandler();

        assertFalse(selectorStub.keys.isEmpty());
        SelectionKey selectionKey = selectorStub.keys.get(0);
        assertEquals(eventHandler.getChannel(), selectionKey.channel());
        assertEquals(0, eventHandler.numInvocations);
    }

    private void registerEventHandler() throws IOException {
        eventHandler.setUseSelectThreadToWait(true);
        eventHandler.setInterestOps(SelectionKey.OP_READ);
        selector.registerForEvent(eventHandler);
        selector.runSelectionLoopOnce();
    }

    @Test
    public void whenIoAvailable_dispatchToEventHandler() throws IOException {
        registerEventHandler();

        selectorStub.setReady(channel, SelectionKey.OP_READ);
        selector.runSelectionLoopOnce();
        assertEquals(1, eventHandler.numInvocations);
    }

    @Test
    public void whenEventTimesOut_reportTimeout() throws IOException {
        registerEventHandler();

        timer.setCurrentTime(1000);
        selector.runSelectionLoopOnce();
        assertTrue(eventHandler.timeout);
    }

    @Test
    public void whenEventHappens_resetTheTimer() throws IOException {
        registerEventHandler();

        timer.setCurrentTime(1000);
        selectorStub.setReady(channel, SelectionKey.OP_READ);
        selector.runSelectionLoopOnce();
        assertFalse(eventHandler.timeout);
    }


    protected void setPrivateFieldValue(Object obj, String fieldName, Object value) {
        try {
            Class theClass = obj.getClass();
            setPrivateFieldValue(obj, theClass, fieldName, value);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPrivateFieldValue(Object obj, Class theClass, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        try {
            Field field = theClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException e) {
            if (theClass.equals(Object.class))
                throw e;
            else
                setPrivateFieldValue(obj, theClass.getSuperclass(), fieldName, value);
        }
    }

    static class EventHandlerFake extends EventHandlerBase implements Timeoutable, Work {
        private int interestOps;
        private SelectableChannelFake channel;
        public int numInvocations;
        private boolean timeout;

        public void setInterestOps(int interestOps) {
            this.interestOps = interestOps;
        }

        @Override
        public int getInterestOps() {
            return interestOps;
        }

        @Override
        public Acceptor getAcceptor() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public SelectableChannel getChannel() {
            return channel;
        }

        @Override
        public Work getWork() {
            return this;
        }

        @Override
        public void doWork() {
            numInvocations++;
            channel.clearReadyOps();
        }

        @Override
        public void setEnqueueTime(long timeInMillis) {
        }

        @Override
        public long getEnqueueTime() {
            return 0;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public void checkForTimeout(long timeSinceLastActivity) {
            timeout = timeSinceLastActivity > 100;
        }
    }

    @SimpleStub
    static abstract class NioSelectorFake extends Selector {
        boolean wakeupCalled;
        boolean open;
        List<SelectionKey> keys = new ArrayList<SelectionKey>();

        Set<SelectionKey> selectedKeys = new HashSet<SelectionKey>();
        Set<SelectionKey> activeKeys = new HashSet<SelectionKey>();

        public void addKey(SelectionKeyFake selectionKey) {
            keys.add(selectionKey);
        }

        public void setReady(SelectableChannel channel, int op) {
            for (SelectionKey key : keys) {
                if (key.channel().equals(channel)) {
                    activeKeys.add(key);
                    ((SelectionKeyFake) key).setReady(op);
                }
            }
        }

        @Override
        public Selector wakeup() {
            wakeupCalled = true;
            return this;
        }

        @Override
        public int select(long timeout) throws IOException {
            selectedKeys.addAll(activeKeys);
            activeKeys.clear();
            return selectedKeys.size();
        }

        @Override
        public Set<SelectionKey> selectedKeys() {
            return selectedKeys;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }
    }

    @SimpleStub
    static abstract class SelectableChannelFake extends SelectableChannel {
        private Set<SelectionKeyFake> keys = new HashSet<SelectionKeyFake>();

        @Override
        public SelectionKey register(Selector sel, int ops, Object attachment) throws ClosedChannelException {
            SelectionKeyFake selectionKey = new SelectionKeyFake(this, ops, sel);
            selectionKey.attach(attachment);
            ((NioSelectorFake) sel).addKey(selectionKey);
            keys.add(selectionKey);
            return selectionKey;
        }


        public void clearReadyOps() {
            for (SelectionKeyFake key : keys)
                key.readyOps = 0;
        }
    }

    static class SelectionKeyFake extends SelectionKey {
        private SelectableChannel channel;
        private int interestOps;
        private Selector selector;
        private int readyOps;

        SelectionKeyFake(SelectableChannel channel, int interestOps, Selector selector) {
            this.channel = channel;
            this.interestOps = interestOps;
            this.selector = selector;
        }

        public SelectableChannel channel() {
            return channel;
        }

        @Override
        public Selector selector() {
            return selector;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void cancel() {
        }

        @Override
        public int interestOps() {
            return interestOps;
        }

        @Override
        public SelectionKey interestOps(int ops) {
            interestOps = ops;
            return this;
        }

        @Override
        public int readyOps() {
            return readyOps;
        }

        void setReady(int op) {
            readyOps |= op;
        }
    }

    static class TimerFake implements SelectorImpl.Timer {
        private long currentTime;

        @Override
        public long getCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(long currentTime) {
            this.currentTime = currentTime;
        }
    }

}
