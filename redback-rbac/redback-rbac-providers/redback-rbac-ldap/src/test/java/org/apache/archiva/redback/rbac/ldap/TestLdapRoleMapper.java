package org.apache.archiva.redback.rbac.ldap;
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
import org.apache.archiva.redback.components.apacheds.ApacheDs;
import org.apache.archiva.redback.policy.PasswordEncoder;
import org.apache.archiva.redback.policy.encoders.SHA1PasswordEncoder;
import org.apache.archiva.redback.users.UserManager;
import org.apache.archiva.redback.users.ldap.service.LdapCacheService;
import org.fest.assertions.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.List;

/**
 * @author Olivier Lamy
 */
@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( locations = { "classpath*:/META-INF/spring-context.xml", "classpath*:/spring-context.xml" } )
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD )
public class TestLdapRoleMapper
    extends TestCase
{

    Logger log = LoggerFactory.getLogger( getClass() );

    @Inject
    @Named( value = "userManager#ldap" )
    private UserManager userManager;

    @Inject
    @Named( value = "apacheDS#test" )
    private ApacheDs apacheDs;

    private String suffix;

    private String groupSuffix;

    private PasswordEncoder passwordEncoder;

    @Inject
    private LdapCacheService ldapCacheService;

    @Inject
    @Named( value = "ldapRoleMapper#test" )
    LdapRoleMapper ldapRoleMapper;

    @Before
    public void setUp()
        throws Exception
    {
        super.setUp();

        passwordEncoder = new SHA1PasswordEncoder();

        groupSuffix = "dc=archiva,dc=apache,dc=org";
        log.info( "groupSuffix: {}", groupSuffix );

        suffix = apacheDs.addSimplePartition( "test", new String[]{ "archiva", "apache", "org" } ).getSuffix();

        log.info( "DN Suffix: {}", suffix );

        apacheDs.startServer();

        clearManyUsers();

        makeUsers();

        createGroups();
    }

    @After
    public void tearDown()
        throws Exception
    {
        // clear cache
        ldapCacheService.removeAllUsers();

        InitialDirContext context = apacheDs.getAdminContext();

        context.unbind( createDn( "admin" ) );

        context.unbind( createDn( "user.7" ) );

        context.unbind( createGroupDn( "internal-repo-manager" ) );

        context.unbind( createGroupDn( "archiva-admin" ) );

        apacheDs.stopServer();

        super.tearDown();
    }

    private void createGroups()
        throws Exception
    {
        InitialDirContext context = apacheDs.getAdminContext();

        createGroup( context, "internal-repo-manager", createGroupDn( "internal-repo-manager" ) );

        createGroup( context, "archiva-admin", createGroupDn( "archiva-admin" ) );
    }

    private void createGroup( DirContext context, String groupName, String dn )
        throws Exception
    {

        Attributes attributes = new BasicAttributes( true );
        BasicAttribute objectClass = new BasicAttribute( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "groupOfUniqueNames" );
        attributes.put( objectClass );
        attributes.put( "cn", groupName );
        BasicAttribute basicAttribute = new BasicAttribute( "uniquemember"  );
        basicAttribute.add( "uid=admin,dc=archiva,dc=apache,dc=org" );
        basicAttribute.add( "uid=user.7,dc=archiva,dc=apache,dc=org" );
        attributes.put( basicAttribute );
        //attributes.put( "uniquemember", "uid=admin,dc=archiva,dc=apache,dc=org" );
        //attributes.put( "uniquemember", "uid=user.7,dc=archiva,dc=apache,dc=org" );

        context.createSubcontext( dn, attributes );
    }

    private void bindUserObject( DirContext context, String cn, String dn )
        throws Exception
    {
        Attributes attributes = new BasicAttributes( true );
        BasicAttribute objectClass = new BasicAttribute( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "inetOrgPerson" );
        objectClass.add( "person" );
        objectClass.add( "organizationalperson" );
        attributes.put( objectClass );
        attributes.put( "cn", cn );
        attributes.put( "sn", "foo" );
        attributes.put( "mail", cn + "@apache.org" );
        attributes.put( "userPassword", passwordEncoder.encodePassword( "foo" ) );
        attributes.put( "givenName", "foo" );
        context.createSubcontext( dn, attributes );
    }

    private void makeUsers()
        throws Exception
    {
        InitialDirContext context = apacheDs.getAdminContext();

        String cn = "admin";
        bindUserObject( context, cn, createDn( cn ) );
        assertExist( context, createDn( cn ), "cn", cn );

        cn = "user.7";
        bindUserObject( context, cn, createDn( cn ) );
        assertExist( context, createDn( cn ), "cn", cn );

    }

    private void clearManyUsers()
        throws Exception
    {
        InitialDirContext context = apacheDs.getAdminContext();

        for ( int i = 0; i < 10000; i++ )
        {
            String cn = "user" + i;
            try
            {
                context.unbind( createDn( cn ) );
            }
            catch ( NamingException e )
            {
                // OK lets try with next one
            }
        }

    }


    private void assertExist( DirContext context, String dn, String attribute, String value )
        throws NamingException
    {
        SearchControls ctls = new SearchControls();

        ctls.setDerefLinkFlag( true );
        ctls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        ctls.setReturningAttributes( new String[]{ "*" } );

        BasicAttributes matchingAttributes = new BasicAttributes();
        matchingAttributes.put( attribute, value );
        BasicAttribute objectClass = new BasicAttribute( "objectClass" );
        objectClass.add( "inetOrgPerson" );
        matchingAttributes.put( objectClass );

        NamingEnumeration<SearchResult> results = context.search( suffix, matchingAttributes );

        assertTrue( results.hasMoreElements() );
        SearchResult result = results.nextElement();
        Attributes attrs = result.getAttributes();
        Attribute testAttr = attrs.get( attribute );
        assertEquals( value, testAttr.get() );

    }

    private String createDn( String cn )
    {
        return "cn=" + cn + "," + suffix;
    }

    private String createGroupDn( String cn )
    {
        return "cn=" + cn + "," + groupSuffix;
    }

    @Test
    public void getAllGroups()
        throws Exception
    {
        List<String> allGroups = ldapRoleMapper.getAllGroups();

        log.info( "allGroups: {}", allGroups );

        Assertions.assertThat( allGroups ).isNotNull().isNotEmpty().contains( "archiva-admin",
                                                                              "internal-repo-manager" );
    }

    @Test
    public void getGroupsMember()
        throws Exception
    {
        List<String> users = ldapRoleMapper.getGroupsMember( "archiva-admin" );

        log.info( "users for archiva-admin: {}", users );

        Assertions.assertThat( users ).isNotNull().isNotEmpty().contains( "admin", "user.7" );
    }

    @Test
    public void getGroups()
        throws Exception
    {
        List<String> roles = ldapRoleMapper.getGroups( "admin" );

        log.info( "roles for admin: {}", roles );

        Assertions.assertThat( roles ).isNotNull().isNotEmpty().contains( "archiva-admin", "internal-repo-manager" );
    }
}