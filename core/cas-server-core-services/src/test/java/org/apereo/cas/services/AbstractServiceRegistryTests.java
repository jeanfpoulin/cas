package org.apereo.cas.services;

import org.apereo.cas.authentication.principal.ShibbolethCompatiblePersistentIdGenerator;
import org.apereo.cas.services.consent.DefaultRegisteredServiceConsentPolicy;
import org.apereo.cas.services.support.RegisteredServiceMappedRegexAttributeFilter;
import org.apereo.cas.services.support.RegisteredServiceRegexAttributeFilter;
import org.apereo.cas.support.oauth.services.OAuthRegisteredService;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.ws.idp.services.WSFederationRegisteredService;

import com.google.common.collect.ArrayListMultimap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;
import org.joda.time.DateTimeUtils;
import org.jooq.lambda.Unchecked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstracted service registry tests for all implementations.
 *
 * @author Timur Duehr
 * @since 5.3.0
 */
@Getter
@RequiredArgsConstructor
public abstract class AbstractServiceRegistryTests {
    public static final int LOAD_SIZE = 1;

    private ServiceRegistry serviceRegistry;

    public static Stream<Class<? extends RegisteredService>> getParameters() {
        return Stream.of(
            RegexRegisteredService.class,
            OAuthRegisteredService.class,
            SamlRegisteredService.class,
            OidcRegisteredService.class,
            WSFederationRegisteredService.class
        );
    }

    @BeforeEach
    public void setUp() {
        this.serviceRegistry = getNewServiceRegistry();
        clearServiceRegistry();
        initializeServiceRegistry();
    }

    @AfterEach
    public void tearDown() {
        clearServiceRegistry();
        tearDownServiceRegistry();
    }

    /**
     * Abstract method to retrieve a new service registry. Implementing classes
     * return the ServiceRegistry they wish to test.
     *
     * @return the ServiceRegistry we wish to test
     */
    protected abstract ServiceRegistry getNewServiceRegistry();

    public void verifyEmptyRegistry() {
        assertEquals(0, serviceRegistry.load().size(), "Loaded too many");
        assertEquals(0, serviceRegistry.size(), "Counted too many");
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySave(final Class<? extends RegisteredService> registeredServiceClass) {
        val svc = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        assertEquals(serviceRegistry.save(svc).getServiceId(), svc.getServiceId());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveAndLoad(final Class<? extends RegisteredService> registeredServiceClass) {
        for (int i = 0; i < getLoadSize(); i++) {
            val svc = buildRegisteredServiceInstance(i, registeredServiceClass);
            this.serviceRegistry.save(svc);
            val svc2 = this.serviceRegistry.findServiceByExactServiceName(svc.getName());
            assertNotNull(svc2);
            this.serviceRegistry.delete(svc2);
        }
        assertTrue(this.serviceRegistry.load().isEmpty());
    }

    @Test
    public void verifyNonExistingService() {
        assertNull(this.serviceRegistry.findServiceById(9999991));
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySavingServices(final Class<? extends RegisteredService> registeredServiceClass) {
        this.serviceRegistry.save(buildRegisteredServiceInstance(100, registeredServiceClass));
        val services = this.serviceRegistry.load();
        assertEquals(1, services.size());
        assertEquals(1, serviceRegistry.size());
        this.serviceRegistry.save(buildRegisteredServiceInstance(101, registeredServiceClass));
        val services2 = this.serviceRegistry.load();
        assertEquals(2, services2.size());
        assertEquals(2, serviceRegistry.size());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyUpdatingServices(final Class<? extends RegisteredService> registeredServiceClass) {
        this.serviceRegistry.save(buildRegisteredServiceInstance(200, registeredServiceClass));
        val services = this.serviceRegistry.load();
        assertFalse(services.isEmpty());
        val rs = (AbstractRegisteredService) this.serviceRegistry.findServiceById(services.stream().findFirst().orElse(null).getId());
        assertNotNull(rs);
        rs.setEvaluationOrder(9999);
        rs.setUsernameAttributeProvider(new DefaultRegisteredServiceUsernameProvider());
        rs.setName("Another Test Service");
        rs.setDescription("The new description");
        rs.setServiceId("https://hello.world");
        rs.setProxyPolicy(new RegexMatchingRegisteredServiceProxyPolicy("https"));
        rs.setAttributeReleasePolicy(new ReturnAllowedAttributeReleasePolicy());
        assertNotNull(this.serviceRegistry.save(rs));

        val rs3 = this.serviceRegistry.findServiceById(rs.getId());
        assertEquals(rs3.getName(), rs.getName());
        assertEquals(rs3.getDescription(), rs.getDescription());
        assertEquals(rs3.getEvaluationOrder(), rs.getEvaluationOrder());
        assertEquals(rs3.getUsernameAttributeProvider(), rs.getUsernameAttributeProvider());
        assertEquals(rs3.getProxyPolicy(), rs.getProxyPolicy());
        assertEquals(rs3.getUsernameAttributeProvider(), rs.getUsernameAttributeProvider());
        assertEquals(rs3.getServiceId(), rs.getServiceId());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyDeletingSingleService(final Class<? extends RegisteredService> registeredServiceClass) {
        val rs = buildRegisteredServiceInstance(300, registeredServiceClass);
        val rs2 = buildRegisteredServiceInstance(301, registeredServiceClass);
        this.serviceRegistry.save(rs2);
        this.serviceRegistry.save(rs);
        this.serviceRegistry.load();
        this.serviceRegistry.delete(rs2);

        assertFalse(this.serviceRegistry.load().isEmpty());

        val rsNew = this.serviceRegistry.findServiceByExactServiceName(rs.getName());
        assertNotNull(rsNew);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyDeletingServices(final Class<? extends RegisteredService> registeredServiceClass) {
        this.serviceRegistry.save(buildRegisteredServiceInstance(400, registeredServiceClass));
        this.serviceRegistry.save(buildRegisteredServiceInstance(401, registeredServiceClass));
        val services = this.serviceRegistry.load();
        services.forEach(registeredService -> this.serviceRegistry.delete(registeredService));
        assertEquals(0, this.serviceRegistry.load().size());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyExpiredServiceDeleted(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setExpirationPolicy(new DefaultRegisteredServiceExpirationPolicy(true, LocalDateTime.now().minusSeconds(1)));
        val r2 = this.serviceRegistry.save(r);
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis() + 2000);
        this.serviceRegistry.load();
        val svc = this.serviceRegistry.findServiceByExactServiceName(r2.getName());
        assertNotNull(svc);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyExpiredServiceDisabled(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val expirationDate = LocalDateTime.now().plusSeconds(1);
        r.setExpirationPolicy(new DefaultRegisteredServiceExpirationPolicy(false, expirationDate));
        val r2 = this.serviceRegistry.save(r);
        val svc = this.serviceRegistry.findServiceByExactServiceName(r2.getName());
        assertNotNull(svc);
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis() + 2000);
        val svc2 = this.serviceRegistry.findServiceByExactServiceName(r2.getName());
        assertNotNull(svc2);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void checkSaveMethodWithNonExistentServiceAndNoAttributes(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
        assertNotNull(this.serviceRegistry.findServiceByExactServiceId(r.getServiceId()));
        assertNotNull(this.serviceRegistry.findServiceByExactServiceName(r.getName()));
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void checkSaveMethodWithDelegatedAuthnPolicy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val strategy = new DefaultRegisteredServiceAccessStrategy();
        strategy.setDelegatedAuthenticationPolicy(
            new DefaultRegisteredServiceDelegatedAuthenticationPolicy(CollectionUtils.wrapList("one", "two")));
        r.setAccessStrategy(strategy);
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void execSaveWithAuthnMethodPolicy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val policy =
            new DefaultRegisteredServiceMultifactorPolicy();
        policy.setFailureMode(RegisteredServiceMultifactorPolicy.FailureModes.PHANTOM);

        val set = new HashSet<String>();
        set.add("duoAuthenticationProvider");
        policy.setMultifactorAuthenticationProviders(set);
        policy.setPrincipalAttributeNameTrigger("memberOf");
        policy.setPrincipalAttributeValueToMatch("cas|CAS|admin");
        r.setMultifactorPolicy(policy);
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void execSaveMethodWithDefaultUsernameAttribute(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setUsernameAttributeProvider(new DefaultRegisteredServiceUsernameProvider());
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void execSaveMethodWithConsentPolicy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val policy = new ReturnAllAttributeReleasePolicy();
        policy.setConsentPolicy(new DefaultRegisteredServiceConsentPolicy(CollectionUtils.wrapSet("test"),
            CollectionUtils.wrapSet("test")));
        r.setAttributeReleasePolicy(policy);
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void ensureSaveMethodWithDefaultPrincipalAttribute(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setUsernameAttributeProvider(new PrincipalAttributeRegisteredServiceUsernameProvider("cn", "UPPER"));
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveMethodWithDefaultAnonymousAttribute(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setUsernameAttributeProvider(new AnonymousRegisteredServiceUsernameAttributeProvider(
            new ShibbolethCompatiblePersistentIdGenerator("helloworld")
        ));
        val r2 = this.serviceRegistry.save(r);
        this.serviceRegistry.load();
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        val anon =
            (AnonymousRegisteredServiceUsernameAttributeProvider) r3.getUsernameAttributeProvider();
        val ss =
            (ShibbolethCompatiblePersistentIdGenerator) anon.getPersistentIdGenerator();
        assertEquals("helloworld", ss.getSalt());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyServiceExpirationPolicy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setExpirationPolicy(new DefaultRegisteredServiceExpirationPolicy(true, LocalDate.now()));
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
        assertNotNull(r3.getExpirationPolicy());
        assertEquals(r2.getExpirationPolicy(), r3.getExpirationPolicy());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveAttributeReleasePolicy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setAttributeReleasePolicy(new ReturnAllAttributeReleasePolicy());
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
        assertNotNull(r3.getAttributeReleasePolicy());
        assertEquals(r2.getAttributeReleasePolicy(), r3.getAttributeReleasePolicy());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveMethodWithExistingServiceNoAttribute(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        this.serviceRegistry.save(r);
        r.setTheme("mytheme");

        this.serviceRegistry.save(r);
        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveAttributeReleasePolicyMappingRules(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val map = ArrayListMultimap.<String, Object>create();
        map.put("attr1", "newattr1");
        map.put("attr2", "newattr2");
        map.put("attr2", "newattr3");

        val policy = new ReturnMappedAttributeReleasePolicy();
        policy.setAllowedAttributes(CollectionUtils.wrap(map));
        r.setAttributeReleasePolicy(policy);

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());

        assertEquals(r2, r3);
        assertNotNull(r3.getAttributeReleasePolicy());
        assertEquals(r2.getAttributeReleasePolicy(), r3.getAttributeReleasePolicy());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveAttributeReleasePolicyAllowedAttrRules(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val policy = new ReturnAllowedAttributeReleasePolicy();
        policy.setAllowedAttributes(Arrays.asList("1", "2", "3"));
        r.setAttributeReleasePolicy(policy);

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());

        assertEquals(r2, r3);
        assertNotNull(r3.getAttributeReleasePolicy());
        assertEquals(r2.getAttributeReleasePolicy(), r3.getAttributeReleasePolicy());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifySaveAttributeReleasePolicyAllowedAttrRulesAndFilter(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setAccessStrategy(new DefaultRegisteredServiceAccessStrategy(true, false));
        r.setProxyPolicy(new RegexMatchingRegisteredServiceProxyPolicy("https://.+"));
        r.setRequiredHandlers(CollectionUtils.wrapHashSet("h1", "h2"));

        val policy = new ReturnAllowedAttributeReleasePolicy();
        policy.setAllowedAttributes(Arrays.asList("1", "2", "3"));
        r.setAttributeReleasePolicy(policy);
        r.getAttributeReleasePolicy().setAttributeFilter(new RegisteredServiceRegexAttributeFilter("\\w+"));

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());

        assertEquals(r2, r3);
        assertNotNull(r3.getAttributeReleasePolicy());
        assertEquals(r2.getAttributeReleasePolicy(), r3.getAttributeReleasePolicy());
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyServiceType(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val r2 = this.serviceRegistry.save(r);
        assertTrue(r2 instanceof RegexRegisteredService);
    }

    @ParameterizedTest
    @ValueSource(classes = {
        RegexRegisteredService.class,
        OAuthRegisteredService.class,
        SamlRegisteredService.class,
        OidcRegisteredService.class,
        WSFederationRegisteredService.class
    })
    public void verifyServiceRemovals(final Class<? extends RegisteredService> registeredServiceClass) {
        val list = new ArrayList<RegisteredService>(5);
        IntStream.range(1, 5).forEach(i -> {
            val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
            list.add(this.serviceRegistry.save(r));
        });

        list.forEach(Unchecked.consumer(r2 -> {
            Thread.sleep(500);
            this.serviceRegistry.delete(r2);
            Thread.sleep(2000);
            assertNull(this.serviceRegistry.findServiceById(r2.getId()));
        }));
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void checkForAuthorizationStrategy(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val authz = new DefaultRegisteredServiceAccessStrategy(false, false);

        val attrs = new HashMap<String, Set<String>>();
        attrs.put("cn", Collections.singleton("v1, v2, v3"));
        attrs.put("memberOf", Collections.singleton("v4, v5, v6"));
        authz.setRequiredAttributes(attrs);
        r.setAccessStrategy(authz);

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyAccessStrategyWithStarEndDate(final Class<? extends RegisteredService> registeredServiceClass) throws Exception {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val authz =
            new TimeBasedRegisteredServiceAccessStrategy(true, false);

        authz.setStartingDateTime(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).toString());
        authz.setEndingDateTime(ZonedDateTime.now(ZoneOffset.UTC).plusDays(10).toString());

        authz.setUnauthorizedRedirectUrl(new URI("https://www.github.com"));
        r.setAccessStrategy(authz);

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyAccessStrategyWithEndpoint(final Class<? extends RegisteredService> registeredServiceClass) throws Exception {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val authz = new RemoteEndpointServiceAccessStrategy();
        authz.setEndpointUrl("http://www.google.com?this=that");
        authz.setAcceptableResponseCodes("200,405,403");
        authz.setUnauthorizedRedirectUrl(new URI("https://www.github.com"));
        r.setAccessStrategy(authz);

        val r2 = this.serviceRegistry.save(r);
        val r3 = this.serviceRegistry.findServiceById(r2.getId());
        assertEquals(r2, r3);
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void serializePublicKeyForServiceAndVerify(final Class<? extends RegisteredService> registeredServiceClass) {
        val publicKey = new RegisteredServicePublicKeyImpl("classpath:RSA1024Public.key", "RSA");
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        r.setPublicKey(publicKey);

        this.serviceRegistry.save(r);
        this.serviceRegistry.load();
        assertNotNull(this.serviceRegistry.findServiceByExactServiceName(r.getName()));
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyMappedRegexAttributeFilter(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);

        val p = new ReturnAllowedAttributeReleasePolicy();
        val filter = new RegisteredServiceMappedRegexAttributeFilter();
        filter.setCompleteMatch(true);
        filter.setPatterns(CollectionUtils.wrap("one", "two"));
        p.setAttributeFilter(filter);

        r.setAttributeReleasePolicy(p);
        this.serviceRegistry.save(r);
        this.serviceRegistry.load();
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void verifyServiceContacts(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);
        val contact = new DefaultRegisteredServiceContact();
        contact.setDepartment("Department");
        contact.setEmail("cas@example.org");
        contact.setName("Contact");
        contact.setPhone("123-456-7890");
        r.setContacts(CollectionUtils.wrap(contact));
        this.serviceRegistry.save(r);
        this.serviceRegistry.load();
    }

    @ParameterizedTest
    @MethodSource("getParameters")
    public void persistCustomServiceProperties(final Class<? extends RegisteredService> registeredServiceClass) {
        val r = buildRegisteredServiceInstance(RandomUtils.nextInt(), registeredServiceClass);

        val properties = new HashMap<String, RegisteredServiceProperty>();
        val property = new DefaultRegisteredServiceProperty();
        val values = new HashSet<String>();
        values.add("value1");
        values.add("value2");
        property.setValues(values);
        properties.put("field1", property);

        val property2 = new DefaultRegisteredServiceProperty();
        val values2 = new HashSet<String>();
        values2.add("value12");
        values2.add("value22");
        property2.setValues(values2);
        properties.put("field2", property2);

        r.setProperties(properties);

        this.serviceRegistry.save(r);
        this.serviceRegistry.load();
        assertNotNull(this.serviceRegistry.findServiceByExactServiceName(r.getName()));
        assertEquals(2, r.getProperties().size());
        assertNotNull(r.getProperties().get("field1"));

        val prop = r.getProperties().get("field1");
        assertEquals(2, prop.getValues().size());
    }

    /**
     * Method to mock RegisteredService objects for testing.
     *
     * @param randomId addition to service name for uniqueness.
     * @return new registered service object
     */
    protected AbstractRegisteredService buildRegisteredServiceInstance(final int randomId, final Class<? extends RegisteredService> registeredServiceClass) {
        val id = String.format("^http://www.serviceid%s.org", randomId);
        val rs = RegisteredServiceTestUtils.getRegisteredService(id, registeredServiceClass);
        initializeServiceInstance(rs);
        return rs;
    }

    /**
     * Method to prepare registered service for testing.
     * Implementing classes may override this if more is necessary.
     */
    protected AbstractRegisteredService initializeServiceInstance(final AbstractRegisteredService rs) {
        val propertyMap = new HashMap<String, RegisteredServiceProperty>();
        val property = new DefaultRegisteredServiceProperty();
        val values = new HashSet<String>();
        values.add("value1");
        values.add("value2");
        property.setValues(values);
        propertyMap.put("field1", property);
        rs.setProperties(propertyMap);
        return rs;
    }

    protected int getLoadSize() {
        return LOAD_SIZE;
    }

    /**
     * Method to prepare the service registry for testing.
     * Implementing classes may override this if more is necessary.
     */
    protected void initializeServiceRegistry() {
    }

    /**
     * Method to shut down the service registry after testing.
     * Implementing classes may override this if more is necessary.
     */
    protected void tearDownServiceRegistry() {
    }

    protected void clearServiceRegistry() {
        val results = this.getServiceRegistry().load();
        results.forEach(service -> this.getServiceRegistry().delete(service));
    }
}
