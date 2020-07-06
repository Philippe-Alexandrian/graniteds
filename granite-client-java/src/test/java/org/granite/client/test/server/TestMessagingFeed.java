/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2015 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.client.test.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.granite.client.messaging.Consumer;
import org.granite.client.messaging.ResultIssuesResponseListener;
import org.granite.client.messaging.ServerApp;
import org.granite.client.messaging.TopicMessageListener;
import org.granite.client.messaging.channel.Channel;
import org.granite.client.messaging.channel.ChannelFactory;
import org.granite.client.messaging.channel.MessagingChannel;
import org.granite.client.messaging.events.IssueEvent;
import org.granite.client.messaging.events.ResultEvent;
import org.granite.client.messaging.events.TopicMessageEvent;
import org.granite.client.test.server.feed.FeedApplication;
import org.granite.client.test.server.feed.FeedListener;
import org.granite.client.test.server.feed.Info;
import org.granite.logging.Logger;
import org.granite.test.container.EmbeddedContainer;
import org.granite.util.ContentType;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Created by william on 30/09/13.
 */
@RunWith(Parameterized.class)
public class TestMessagingFeed {

    private static final Logger log = Logger.getLogger(TestMessagingFeed.class);

    private static final String APP_NAME = "feed";

    @Parameterized.Parameters(name = "container: {0}, encoding: {1}, channel: {2}")
    public static Iterable<Object[]> data() {
	return ContainerTestUtil.data();
    }

    private ContentType contentType;
    private String channelType;
    protected static EmbeddedContainer container;

    private static final ServerApp SERVER_APP = new ServerApp("/" + APP_NAME, false, "localhost", 8787);

    public TestMessagingFeed(String containerClassName, ContentType contentType, String channelType) {
	this.contentType = contentType;
	this.channelType = channelType;
    }

    @BeforeClass
    public static void startContainer() throws Exception {
	// Build a feed server application
	WebArchive war = ShrinkWrap.create(WebArchive.class, APP_NAME + ".war");
	war.addClasses(FeedApplication.class, FeedListener.class, Info.class);

	container = ContainerTestUtil.newContainer(war, false);
	container.start();
	log.info("Container started");
    }

    @AfterClass
    public static void stopContainer() throws Exception {
	container.stop();
	container.destroy();
	log.info("Container stopped");
    }

    @Test
    public void testFeedSingleConsumer() throws Exception {
	log.info("TestMessagingFeed.testFeedSingleConsumer %s - %s", this.channelType, this.contentType);
	CyclicBarrier[] barriers = new CyclicBarrier[3];
	barriers[0] = new CyclicBarrier(2);
	barriers[1] = new CyclicBarrier(2);
	barriers[2] = new CyclicBarrier(2);

	ConsumerThread consumer = new ConsumerThread("C", barriers);
	consumer.start();

	try {
	    barriers[0].await(5, TimeUnit.SECONDS);
	} catch (TimeoutException e) {
	    log.error(e, "Consumer subscription timeout");
	    Assert.fail("Consumer subscription failed");
	}

	try {
	    barriers[1].await(10, TimeUnit.SECONDS);
	} catch (TimeoutException e) {
	    log.error(e, "Consumer reception timeout");
	    Assert.fail("Consumer receive messages failed");
	}

	try {
	    barriers[2].await(5, TimeUnit.SECONDS);
	} catch (TimeoutException e) {
	    log.error(e, "Consumer unsubscription timeout");
	    Assert.fail("Consumer unsubscription failed");
	}
    }

    private static final int CONSUMER_COUNT = 5;

    @Test
    public void testFeedMultiConsumer() throws Exception {
	log.info("TestMessagingFeed.testFeedMultiConsumer %s - %s", this.channelType, this.contentType);
	CyclicBarrier[] barriers = new CyclicBarrier[3];
	barriers[0] = new CyclicBarrier(CONSUMER_COUNT + 1);
	barriers[1] = new CyclicBarrier(CONSUMER_COUNT + 1);
	barriers[2] = new CyclicBarrier(CONSUMER_COUNT + 1);

	for (int i = 0; i < CONSUMER_COUNT; i++) {
	    ConsumerThread consumer = new ConsumerThread("C" + (i + 1), barriers);
	    consumer.start();
	}

	try {
	    barriers[0].await(10, TimeUnit.SECONDS);
	    log.info("All consumers subscribed");
	} catch (TimeoutException e) {
	    log.error(e, "Consumers subscription timeout");
	    Assert.fail("Consumers not subscribed");
	}

	try {
	    barriers[1].await(10, TimeUnit.SECONDS);
	    log.info("All messages received");
	} catch (TimeoutException e) {
	    log.error(e, "Consumers reception timeout");
	    Assert.fail("Consumers receive messages failed");
	}

	try {
	    barriers[2].await(10, TimeUnit.SECONDS);
	    log.info("All consumers unsubscribed");
	} catch (TimeoutException e) {
	    log.error(e, "Consumers unsubscription timeout");
	    Assert.fail("Consumers unsubscription failed");
	}
    }

    protected void waitForChannel(Channel channel, CyclicBarrier barrier) {
	try {
	    barrier.await();
	} catch (Exception e) {
	}
    }

    private class ConsumerThread implements Runnable {

	private String id;
	private List<Info> received = new ArrayList<>();
	private CyclicBarrier[] barriers;
	private Thread thread = new Thread(this);
	private ChannelFactory channelFactory;
	private Consumer consumer;

	public ConsumerThread(String id, CyclicBarrier[] barriers) {
	    this.id = id;
	    this.thread.setName(id);
	    this.barriers = barriers;
	}

	public void start() {
	    this.thread.start();
	}

	private CountDownLatch waitToStop = new CountDownLatch(1);

	@Override
	public void run() {
	    this.channelFactory = ContainerTestUtil.buildChannelFactory(TestMessagingFeed.this.contentType);
	    MessagingChannel channel = this.channelFactory.newMessagingChannel(TestMessagingFeed.this.channelType, "messagingamf-" + this.id, SERVER_APP);

	    this.consumer = new Consumer(channel, "feed", "feed");
	    this.consumer.addMessageListener(new ConsumerMessageListener());
	    this.consumer.subscribe(new ResultIssuesResponseListener() {
		@Override
		public void onResult(ResultEvent event) {
		    log.info("Consumer %s: subscribed %s", ConsumerThread.this.id, event.getResult());
		    waitForChannel(ConsumerThread.this.consumer.getChannel(), ConsumerThread.this.barriers[0]);
		}

		@Override
		public void onIssue(IssueEvent event) {
		    log.error("Consumer %s: subscription failed %s", ConsumerThread.this.id, event.toString());
		}
	    });

	    try {
		if (!this.waitToStop.await(20, TimeUnit.SECONDS)) {
		    log.error("Consumer %s time out", this.id);
		}
	    } catch (Exception e) {
		log.error(e, "Consumer %s interrupted", this.id);
	    }
	    try {
		log.info("Consumer %s stopping", this.id);
		channel.stop();
		this.channelFactory.stop();
		log.info("Consumer %s stopped", this.id);

		this.barriers[2].await();
	    } catch (Exception e) {
		log.error(e, "Consumer %s did not terminate correctly", this.id);
	    }
	}

	private class ConsumerMessageListener implements TopicMessageListener {
	    @Override
	    public void onMessage(TopicMessageEvent event) {
		Info info = (Info) event.getData();
		log.info("Consumer %s: received message %s", ConsumerThread.this.id, event.getData());
		ConsumerThread.this.received.add(info);

		if (ConsumerThread.this.received.size() == 10) {
		    log.info("Consumer %s: received all messages", ConsumerThread.this.id);
		    // All messages received, wait for other consumers before unsubscribing
		    try {
			ConsumerThread.this.barriers[1].await();
		    } catch (Exception e) {
			log.info(e, "Consumer %s interrupted waiting for others", ConsumerThread.this.id);
		    }

		    ConsumerThread.this.consumer.unsubscribe(new ResultIssuesResponseListener() {
			@Override
			public void onResult(ResultEvent event) {
			    log.info("Consumer %s: unsubscribed %s", ConsumerThread.this.id, event.getResult());
			    ConsumerThread.this.waitToStop.countDown();
			}

			@Override
			public void onIssue(IssueEvent event) {
			    log.error("Consumer %s: unsubscription failed %s", ConsumerThread.this.id, event.toString());
			}
		    });
		}
	    }
	}
    }
}
