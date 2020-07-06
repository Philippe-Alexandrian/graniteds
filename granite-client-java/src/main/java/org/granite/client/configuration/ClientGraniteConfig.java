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
package org.granite.client.configuration;

import java.io.ByteArrayInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.granite.config.AMF3Config;
import org.granite.config.ActionScriptClassDescriptorFactory;
import org.granite.config.Config;
import org.granite.config.ConvertersConfig;
import org.granite.config.ExternalizerFactory;
import org.granite.config.ExternalizersConfig;
import org.granite.config.JavaClassDescriptorFactory;
import org.granite.config.api.AliasRegistryConfig;
import org.granite.config.api.ConfigurableFactory;
import org.granite.config.api.Configuration;
import org.granite.config.api.GraniteConfigException;
import org.granite.context.GraniteContext;
import org.granite.logging.Logger;
import org.granite.messaging.AliasRegistry;
import org.granite.messaging.DefaultAliasRegistry;
import org.granite.messaging.amf.io.AMF3Deserializer;
import org.granite.messaging.amf.io.AMF3DeserializerSecurizer;
import org.granite.messaging.amf.io.AMF3Serializer;
import org.granite.messaging.amf.io.convert.Converter;
import org.granite.messaging.amf.io.convert.Converters;
import org.granite.messaging.amf.io.util.ActionScriptClassDescriptor;
import org.granite.messaging.amf.io.util.ClassGetter;
import org.granite.messaging.amf.io.util.DefaultClassGetter;
import org.granite.messaging.amf.io.util.JavaClassDescriptor;
import org.granite.messaging.amf.io.util.externalizer.BigDecimalExternalizer;
import org.granite.messaging.amf.io.util.externalizer.BigIntegerExternalizer;
import org.granite.messaging.amf.io.util.externalizer.Externalizer;
import org.granite.messaging.amf.io.util.externalizer.LongExternalizer;
import org.granite.messaging.amf.io.util.externalizer.MapExternalizer;
import org.granite.scan.ScannedItem;
import org.granite.scan.ScannedItemHandler;
import org.granite.scan.Scanner;
import org.granite.scan.ScannerFactory;
import org.granite.util.StreamUtil;
import org.granite.util.TypeUtil;
import org.granite.util.XMap;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Franck WOLFF
 */
public class ClientGraniteConfig implements ConvertersConfig, AliasRegistryConfig, AMF3Config, ExternalizersConfig, ScannedItemHandler {

    ///////////////////////////////////////////////////////////////////////////
    // Static fields.

    private static final Logger log = Logger.getLogger(ClientGraniteConfig.class);

    private static final String GRANITE_CONFIG_PUBLIC_ID = "-//Granite Data Services//DTD granite-config internal//EN";
    private static final String GRANITE_CONFIG_PROPERTIES = "META-INF/granite-config.properties";

    final ExternalizerFactory EXTERNALIZER_FACTORY = new ExternalizerFactory();
    private static final Externalizer LONG_EXTERNALIZER = new LongExternalizer();
    private static final Externalizer BIGINTEGER_EXTERNALIZER = new BigIntegerExternalizer();
    private static final Externalizer BIGDECIMAL_EXTERNALIZER = new BigDecimalExternalizer();
    private static final Externalizer MAP_EXTERNALIZER = new MapExternalizer();

    final ActionScriptClassDescriptorFactory ASC_DESCRIPTOR_FACTORY = new ActionScriptClassDescriptorFactory();
    final JavaClassDescriptorFactory JC_DESCRIPTOR_FACTORY = new JavaClassDescriptorFactory();

    ///////////////////////////////////////////////////////////////////////////
    // Instance fields.

    // Should we scan classpath for auto-configured services/externalizers?
    private boolean scan = false;

    private AliasRegistry aliasRegistry = new DefaultAliasRegistry();

    // Custom AMF3 (De)Serializer configuration.
    private Constructor<AMF3Serializer> amf3SerializerConstructor = null;
    private Constructor<AMF3Deserializer> amf3DeserializerConstructor = null;

    // Converters configuration.
    private List<Class<? extends Converter>> converterClasses = new ArrayList<>();
    private Converters converters = null;

    // Instantiators configuration.
    private final Map<String, String> instantiators = new HashMap<>();

    // Class getter configuration.
    private ClassGetter classGetter = new DefaultClassGetter();
    private boolean classGetterSet = false;

    // Externalizers configuration.
    private XMap externalizersConfiguration = null;
    private final List<Externalizer> scannedExternalizers = new ArrayList<>();
    private final ConcurrentHashMap<String, Externalizer> externalizersByType = new ConcurrentHashMap<>();
    private final Map<String, String> externalizersByInstanceOf = new HashMap<>();
    private final Map<String, String> externalizersByAnnotatedWith = new HashMap<>();

    // Java descriptors configuration.
    private final ConcurrentHashMap<String, JavaClassDescriptor> javaDescriptorsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Class<? extends JavaClassDescriptor>> javaDescriptorsByType = new ConcurrentHashMap<>();
    private final Map<String, String> javaDescriptorsByInstanceOf = new HashMap<>();

    // AS3 descriptors configuration.
    private final ConcurrentHashMap<String, Class<? extends ActionScriptClassDescriptor>> as3DescriptorsByType = new ConcurrentHashMap<>();
    private final Map<String, String> as3DescriptorsByInstanceOf = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////
    // Constructor.

    public ClientGraniteConfig(String stdConfig, InputStream customConfigIs, Configuration configuration, String MBeanContextName) throws IOException, SAXException {
	try {
	    this.amf3SerializerConstructor = TypeUtil.getConstructor(AMF3Serializer.class, new Class<?>[] { OutputStream.class });
	    this.amf3DeserializerConstructor = TypeUtil.getConstructor(AMF3Deserializer.class, new Class<?>[] { InputStream.class });
	} catch (Exception e) {
	    throw new GraniteConfigException("Could not get constructor for AMF3 (de)serializers", e);
	}

	ClassLoader loader = ClientGraniteConfig.class.getClassLoader();

	final ByteArrayInputStream dtd = StreamUtil.getResourceAsStream("org/granite/config/granite-config.dtd", loader);
	final EntityResolver resolver = (publicId, systemId) -> {
	    if (GRANITE_CONFIG_PUBLIC_ID.equals(publicId)) {
		dtd.reset();
		InputSource source = new InputSource(dtd);
		source.setPublicId(publicId);
		return source;
	    }
	    return null;
	};

	// Load standard config.
	InputStream is = null;
	try {
	    is = StreamUtil.getResourceAsStream("org/granite/client/configuration/granite-config.xml", loader);
	    XMap doc = new XMap(is, resolver);
	    forElement(doc, false, null);
	} finally {
	    if (is != null) {
		is.close();
	    }
	}

	if (stdConfig != null) {
	    try {
		is = StreamUtil.getResourceAsStream(stdConfig, loader);
		XMap doc = new XMap(is, resolver);
		forElement(doc, false, null);
	    } finally {
		if (is != null) {
		    is.close();
		}
	    }
	}

	// Load custom config (override).
	if (customConfigIs != null) {
	    XMap doc = new XMap(customConfigIs, resolver);
	    forElement(doc, true, configuration != null ? configuration.getGraniteConfigProperties() : null);
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Classpath scan initialization.

    private void scanConfig(String graniteConfigProperties) {
	// if config overriding exists
	Scanner scanner = ScannerFactory.createScanner(this, graniteConfigProperties != null ? graniteConfigProperties : GRANITE_CONFIG_PROPERTIES);
	try {
	    scanner.scan();
	} catch (Exception e) {
	    log.error(e, "Could not scan classpath for configuration");
	}
    }

    @Override
    public boolean handleMarkerItem(ScannedItem item) {
	try {
	    return handleProperties(item.loadAsProperties());
	} catch (Exception e) {
	    log.error(e, "Could not load properties: %s", item);
	}
	return true;
    }

    @Override
    public void handleScannedItem(ScannedItem item) {
	if ("class".equals(item.getExtension()) && (item.getName().indexOf('$') == -1)) {
	    try {
		handleClass(item.loadAsClass());
	    } catch (NoClassDefFoundError e) {
		// Ignore errors with Tide classes depending on Gravity
	    } catch (LinkageError e) {
		// Ignore errors with GraniteDS/Hibernate classes depending on Hibernate 3 when using Hibernate 4
	    } catch (Throwable t) {
		log.error(t, "Could not load class: %s", item);
	    }
	}
    }

    private boolean handleProperties(Properties properties) {
	if (properties.getProperty("dependsOn") != null) {
	    String dependsOn = properties.getProperty("dependsOn");
	    try {
		TypeUtil.forName(dependsOn);
	    } catch (ClassNotFoundException e) {
		// Class not found, skip scan for this package
		return true;
	    }
	}

	String classGetterName = properties.getProperty("classGetter");
	if (!this.classGetterSet && (classGetterName != null)) {
	    try {
		this.classGetter = TypeUtil.newInstance(classGetterName, ClassGetter.class);
	    } catch (Throwable t) {
		log.error(t, "Could not create instance of: %s", classGetterName);
	    }
	}

	for (Map.Entry<?, ?> me : properties.entrySet()) {
	    if (me.getKey().toString().startsWith("converter.")) {
		String converterName = me.getValue().toString();
		try {
		    this.converterClasses.add(TypeUtil.forName(converterName, Converter.class));
		} catch (Exception e) {
		    throw new GraniteConfigException("Could not get converter class for: " + converterName, e);
		}
	    }
	}

	return false;
    }

    private void handleClass(Class<?> clazz) {
	if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
	    return;
	}

	if (Externalizer.class.isAssignableFrom(clazz)) {
	    try {
		this.scannedExternalizers.add(TypeUtil.newInstance(clazz, Externalizer.class));
	    } catch (Exception e) {
		log.error(e, "Could not create new instance of: %s", clazz);
	    }
	}
    }

    ///////////////////////////////////////////////////////////////////////////
    // Property getters.

    public boolean getScan() {
	return this.scan;
    }

    @Override
    public ObjectOutput newAMF3Serializer(OutputStream out) {
	try {
	    return this.amf3SerializerConstructor.newInstance(new Object[] { out });
	} catch (Exception e) {
	    throw new GraniteConfigException("Could not create serializer instance with: " + this.amf3SerializerConstructor, e);
	}
    }

    public Constructor<?> getAmf3SerializerConstructor() {
	return this.amf3SerializerConstructor;
    }

    @Override
    public ObjectInput newAMF3Deserializer(InputStream in) {
	try {
	    return this.amf3DeserializerConstructor.newInstance(new Object[] { in });
	} catch (Exception e) {
	    throw new GraniteConfigException("Could not create deserializer instance with: " + this.amf3DeserializerConstructor, e);
	}
    }

    public Constructor<?> getAmf3DeserializerConstructor() {
	return this.amf3DeserializerConstructor;
    }

    @Override
    public AMF3DeserializerSecurizer getAmf3DeserializerSecurizer() {
	return null;
    }

    @Override
    public Map<String, String> getInstantiators() {
	return this.instantiators;
    }

    @Override
    public Converters getConverters() {
	return this.converters;
    }

    @Override
    public String getInstantiator(String type) {
	return this.instantiators.get(type);
    }

    @Override
    public ClassGetter getClassGetter() {
	return this.classGetter;
    }

    @Override
    public XMap getExternalizersConfiguration() {
	return this.externalizersConfiguration;
    }

    @Override
    public void setExternalizersConfiguration(XMap externalizersConfiguration) {
	this.externalizersConfiguration = externalizersConfiguration;
    }

    @Override
    public Externalizer getExternalizer(String type) {
	Externalizer externalizer = getElementByType(type, this.EXTERNALIZER_FACTORY, this.externalizersByType, this.externalizersByInstanceOf, this.externalizersByAnnotatedWith,
		this.scannedExternalizers);
	if (externalizer != null) {
	    return externalizer;
	}

	if ("java".equals(GraniteContext.getCurrentInstance().getClientType())) {
	    // Force use of number externalizers when serializing from/to a Java client
	    if (Long.class.getName().equals(type)) {
		return LONG_EXTERNALIZER;
	    } else if (BigInteger.class.getName().equals(type)) {
		return BIGINTEGER_EXTERNALIZER;
	    } else if (BigDecimal.class.getName().equals(type)) {
		return BIGDECIMAL_EXTERNALIZER;
	    } else {
		try {
		    Class<?> clazz = TypeUtil.forName(type);
		    if (Map.class.isAssignableFrom(clazz) && !Externalizable.class.isAssignableFrom(clazz)) {
			return MAP_EXTERNALIZER;
		    }
		} catch (Exception e) {
		    // Rien.
		}
	    }
	}

	return null;
    }

    @Override
    public void registerExternalizer(Externalizer externalizer) {
	this.scannedExternalizers.add(externalizer);
    }

    @Override
    public Map<String, Externalizer> getExternalizersByType() {
	return this.externalizersByType;
    }

    @Override
    public Map<String, String> getExternalizersByInstanceOf() {
	return this.externalizersByInstanceOf;
    }

    @Override
    public Map<String, String> getExternalizersByAnnotatedWith() {
	return this.externalizersByAnnotatedWith;
    }

    @Override
    public List<Externalizer> getScannedExternalizers() {
	return this.scannedExternalizers;
    }

    @Override
    public Externalizer setExternalizersByType(String type, String externalizerType) {
	return this.externalizersByType.put(type, this.EXTERNALIZER_FACTORY.getInstance(externalizerType, this));
    }

    @Override
    public String putExternalizersByInstanceOf(String instanceOf, String externalizerType) {
	return this.externalizersByInstanceOf.put(instanceOf, externalizerType);
    }

    @Override
    public String putExternalizersByAnnotatedWith(String annotatedWith, String externalizerType) {
	return this.externalizersByAnnotatedWith.put(annotatedWith, externalizerType);
    }

    @Override
    public Class<? extends ActionScriptClassDescriptor> getActionScriptDescriptor(String type) {
	return getElementByType(type, this.ASC_DESCRIPTOR_FACTORY, this.as3DescriptorsByType, this.as3DescriptorsByInstanceOf, null, null);
    }

    @Override
    public Map<String, Class<? extends ActionScriptClassDescriptor>> getAs3DescriptorsByType() {
	return this.as3DescriptorsByType;
    }

    @Override
    public Map<String, String> getAs3DescriptorsByInstanceOf() {
	return this.as3DescriptorsByInstanceOf;
    }

    @Override
    public ConcurrentMap<String, JavaClassDescriptor> getJavaDescriptorsCache() {
	return this.javaDescriptorsCache;
    }

    @Override
    public Class<? extends JavaClassDescriptor> getJavaDescriptor(String type) {
	return getElementByType(type, this.JC_DESCRIPTOR_FACTORY, this.javaDescriptorsByType, this.javaDescriptorsByInstanceOf, null, null);
    }

    @Override
    public Map<String, Class<? extends JavaClassDescriptor>> getJavaDescriptorsByType() {
	return this.javaDescriptorsByType;
    }

    @Override
    public Map<String, String> getJavaDescriptorsByInstanceOf() {
	return this.javaDescriptorsByInstanceOf;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Static GraniteConfig loading helpers.

    private void forElement(XMap element, boolean custom, String graniteConfigProperties) {
	String lScan = element.get("@scan");

	this.scan = Boolean.TRUE.toString().equals(lScan);

	loadCustomAMF3Serializer(element, custom);
	loadCustomConverters(element, custom);
	loadCustomInstantiators(element, custom);
	loadCustomClassGetter(element, custom);
	loadCustomExternalizers(element, custom);
	loadCustomDescriptors(element, custom);

	if (this.scan) {
	    scanConfig(graniteConfigProperties);
	}

	finishCustomConverters(custom);
    }

    private void loadCustomAMF3Serializer(XMap element, boolean custom) {
	XMap amf3Serializer = element.getOne("amf3-serializer");
	if (amf3Serializer != null) {
	    String type = amf3Serializer.get("@type");
	    try {
		Class<AMF3Serializer> amf3SerializerClass = TypeUtil.forName(type, AMF3Serializer.class);
		this.amf3SerializerConstructor = TypeUtil.getConstructor(amf3SerializerClass, new Class<?>[] { OutputStream.class });
	    } catch (Exception e) {
		throw new GraniteConfigException("Could not get constructor for AMF3 serializer: " + type, e);
	    }
	}

	XMap amf3Deserializer = element.getOne("amf3-deserializer");
	if (amf3Deserializer != null) {
	    String type = amf3Deserializer.get("@type");
	    try {
		Class<AMF3Deserializer> amf3DeserializerClass = TypeUtil.forName(type, AMF3Deserializer.class);
		this.amf3DeserializerConstructor = TypeUtil.getConstructor(amf3DeserializerClass, new Class<?>[] { InputStream.class });
	    } catch (Exception e) {
		throw new GraniteConfigException("Could not get constructor for AMF3 deserializer: " + type, e);
	    }
	}
    }

    private void loadCustomConverters(XMap element, boolean custom) {
	XMap lConverters = element.getOne("converters");
	if (lConverters != null) {
	    // Should we override standard config converters?
	    String override = lConverters.get("@override");
	    if (Boolean.TRUE.toString().equals(override)) {
		this.converterClasses.clear();
	    }

	    int i = 0;
	    for (XMap converter : lConverters.getAll("converter")) {
		String type = converter.get("@type");
		try {
		    // For custom config, shifts any standard converters to the end of the list...
		    this.converterClasses.add(i++, TypeUtil.forName(type, Converter.class));
		} catch (Exception e) {
		    throw new GraniteConfigException("Could not get converter class for: " + type, e);
		}
	    }
	}
    }

    private void finishCustomConverters(boolean custom) {
	try {
	    this.converters = new Converters(this.converterClasses);
	} catch (Exception e) {
	    throw new GraniteConfigException("Could not construct new Converters instance", e);
	}

	// Cleanup...
	if (custom) {
	    this.converterClasses = null;
	}
    }

    private void loadCustomInstantiators(XMap element, boolean custom) {
	XMap lInstantiators = element.getOne("instantiators");
	if (lInstantiators != null) {
	    for (XMap instantiator : lInstantiators.getAll("instantiator")) {
		this.instantiators.put(instantiator.get("@type"), instantiator.get("."));
	    }
	}
    }

    private void loadCustomClassGetter(XMap element, boolean custom) {
	XMap lClassGetter = element.getOne("class-getter");
	if (lClassGetter != null) {
	    String type = lClassGetter.get("@type");
	    try {
		this.classGetter = (ClassGetter) TypeUtil.newInstance(type);
		this.classGetterSet = true;
	    } catch (Exception e) {
		throw new GraniteConfigException("Could not instantiate ClassGetter: " + type, e);
	    }
	}
    }

    private void loadCustomExternalizers(XMap element, boolean custom) {
	this.externalizersConfiguration = element.getOne("externalizers/configuration");

	for (XMap externalizer : element.getAll("externalizers/externalizer")) {
	    String externalizerType = externalizer.get("@type");

	    for (XMap include : externalizer.getAll("include")) {
		String type = include.get("@type");
		if (type != null) {
		    this.externalizersByType.put(type, this.EXTERNALIZER_FACTORY.getInstance(externalizerType, this));
		} else {
		    String instanceOf = include.get("@instance-of");
		    if (instanceOf != null) {
			this.externalizersByInstanceOf.put(instanceOf, externalizerType);
		    } else {
			String annotatedWith = include.get("@annotated-with");
			if (annotatedWith == null) {
			    throw new GraniteConfigException("Element 'include' has no attribute 'type', 'instance-of' or 'annotated-with'");
			}
			this.externalizersByAnnotatedWith.put(annotatedWith, externalizerType);
		    }
		}
	    }
	}
    }

    /**
     * Read custom class descriptors. Descriptor must have 'type' or 'instanceof' attribute and one of 'java' or 'as3' attributes specified.
     */
    private void loadCustomDescriptors(XMap element, boolean custom) {
	for (XMap descriptor : element.getAll("descriptors/descriptor")) {
	    String type = descriptor.get("@type");
	    if (type != null) {
		String java = descriptor.get("@java");
		String as3 = descriptor.get("@as3");
		if ((java == null) && (as3 == null)) {
		    throw new GraniteConfigException("Element 'descriptor' has no attributes 'java' or 'as3'\n" + descriptor);
		}
		if (java != null) {
		    this.javaDescriptorsByType.put(type, this.JC_DESCRIPTOR_FACTORY.getInstance(java, this));
		}
		if (as3 != null) {
		    this.as3DescriptorsByType.put(type, this.ASC_DESCRIPTOR_FACTORY.getInstance(as3, this));
		}
	    } else {
		String instanceOf = descriptor.get("@instance-of");
		if (instanceOf == null) {
		    throw new GraniteConfigException("Element 'descriptor' has no attribute 'type' or 'instance-of'\n" + descriptor);
		}
		String java = descriptor.get("@java");
		String as3 = descriptor.get("@as3");
		if ((java == null) && (as3 == null)) {
		    throw new GraniteConfigException("Element 'descriptor' has no attributes 'java' or 'as3' in:\n" + descriptor);
		}
		if (java != null) {
		    this.javaDescriptorsByInstanceOf.put(instanceOf, java);
		}
		if (as3 != null) {
		    this.as3DescriptorsByInstanceOf.put(instanceOf, as3);
		}
	    }
	}
    }

    @Override
    public void setAliasRegistry(AliasRegistry aliasRegistry) {
	this.aliasRegistry = aliasRegistry;
    }

    @Override
    public AliasRegistry getAliasRegistry() {
	return this.aliasRegistry;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Other helpers.

    @SuppressWarnings("unchecked")
    private <T, C extends Config> T getElementByType(String type, ConfigurableFactory<T, C> factory, ConcurrentHashMap<String, T> elementsByType,
	    Map<String, String> elementsByInstanceOf, Map<String, String> elementsByAnnotatedWith, List<T> scannedConfigurables) {

	// This NULL object is a Java null placeholder: ConcurrentHashMap doesn't allow
	// null values...
	final T NULL = factory.getNullInstance();

	T element = elementsByType.get(type);
	if (element != null) {
	    return (NULL == element ? null : element);
	}
	element = NULL;

	Class<?> typeClass = null;
	try {
	    typeClass = TypeUtil.forName(type);
	} catch (Exception e) {
	    throw new GraniteConfigException("Could not load class: " + type, e);
	}

	if ((elementsByAnnotatedWith != null) && (NULL == element)) {
	    for (Map.Entry<String, String> entry : elementsByAnnotatedWith.entrySet()) {
		String annotation = entry.getKey();
		try {
		    Class<Annotation> annotationClass = TypeUtil.forName(annotation, Annotation.class);
		    if (typeClass.isAnnotationPresent(annotationClass)) {
			element = factory.getInstance(entry.getValue(), (C) this);
			break;
		    }
		} catch (Exception e) {
		    throw new GraniteConfigException("Could not load class: " + annotation, e);
		}
	    }
	}

	if ((elementsByInstanceOf != null) && (NULL == element)) {
	    for (Map.Entry<String, String> entry : elementsByInstanceOf.entrySet()) {
		String instanceOf = entry.getKey();
		try {
		    Class<?> instanceOfClass = TypeUtil.forName(instanceOf);
		    if (instanceOfClass.isAssignableFrom(typeClass)) {
			element = factory.getInstance(entry.getValue(), (C) this);
			break;
		    }
		} catch (Exception e) {
		    throw new GraniteConfigException("Could not load class: " + instanceOf, e);
		}
	    }
	}

	if (NULL == element) {
	    element = factory.getInstanceForBean(scannedConfigurables, typeClass, (C) this);
	}

	T previous = elementsByType.putIfAbsent(type, element);
	if (previous != null) {
	    element = previous;
	}

	return (NULL == element ? null : element);
    }
}
