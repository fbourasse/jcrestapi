/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2013 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.jcrestapi;

import org.glassfish.hk2.api.Factory;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.core.Application;
import java.util.List;
import java.util.Properties;
import java.util.logging.LogRecord;

import static com.jayway.restassured.RestAssured.expect;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.*;

/**
 * @author Christophe Laprun
 */
public class APITest extends JerseyTest {

    @Override
    protected Application configure() {
        return new APIApplication(TestRepositoryFactory.class);
    }

    @Test
    public void testGetVersion() throws Exception {
        Properties props = new Properties();
        props.load(API.class.getClassLoader().getResourceAsStream("jcrestapi.properties"));

        try {
            expect().statusCode(SC_OK)
                    .contentType("text/plain")
                    .body(equalTo(props.getProperty("jcrestapi.version")))
                    .when().get(generateURL("/api/version"));
        } finally {
            final List<LogRecord> loggedRecords = getLoggedRecords();
            for (LogRecord record : loggedRecords) {
                System.out.println("record = " + record.getMessage());
            }
        }
    }

    @Test
    public void testGetInexistingNode() {
        expect().statusCode(SC_NOT_FOUND)
                .when().get(generateURL("/foo"));

        expect().statusCode(SC_NOT_FOUND)
                .when().get(getURLByPath("foo"));
    }

    @Test
    public void testGetRoot() throws RepositoryException {
        final Session session = TestRepositoryFactory.repository.login();
        final Node rootNode = session.getRootNode();
        final String rootId = rootNode.getIdentifier();
        final String rootTypeName = rootNode.getPrimaryNodeType().getName();
        session.logout();

        expect().statusCode(SC_OK)
                .body(
                        "name", equalTo(""),
                        "type", equalTo("rep:root"),

                        // check that links are present
                        "_links.self.href", equalTo(getURIById(rootId)),
                        "_links.type.href", equalTo(getTypeURIByPath(rootTypeName)),
                        "_links.children.href", equalTo(getChildURI(rootId, "children")),
                        "_links.properties.href", equalTo(getChildURI(rootId, "properties")),
                        "_links.mixins.href", equalTo(getChildURI(rootId, "mixins")),
                        "_links.versions.href", equalTo(getChildURI(rootId, "versions")),

                        // check jcr:primaryType property
                        "properties.jcr__primaryType.name", equalTo("jcr:primaryType"),
                        "properties.jcr__primaryType.value", equalTo("rep:root"),
                        "properties.jcr__primaryType._links.self.href", equalTo(getChildURI(rootId, "properties/jcr__primaryType")),
                        "properties.jcr__primaryType._links.type.href",
                        equalTo(getTypeURIByPath("nt__base/jcr__propertyDefinition--2")),

                        // check jcr:mixinTypes property
                        "properties.jcr__mixinTypes.name", equalTo("jcr:mixinTypes"),
                        "properties.jcr__mixinTypes.value", hasItem("rep:AccessControllable"),
                        "properties.jcr__mixinTypes._links.self.href", equalTo(getChildURI(rootId, "properties/jcr__mixinTypes")),
                        "properties.jcr__mixinTypes._links.type.href",
                        equalTo(getTypeURIByPath("nt__base/jcr__propertyDefinition")),

                        // check that children don't have children (only 1 level deep hierarchy)
                        "children.jcr__system.children", is(nullValue())
                )
                .when().get(getURLByPath(""));
    }

    /*@Test
    public void testThatWeCanAccessValuesAndTypesFromLinks() {
        // get root and its JSON representation
        final Response response = expect().statusCode(SC_OK).when().get(getURLByPath(""));
        final JsonPath rootJSON = response.body().jsonPath();

        // get the root primary type property and check that its name matches the one we got from root object
        final String primaryTypeSelf = rootJSON.getString("properties.jcr__primaryType._links.self.href");

        expect().body(
                "name", equalTo(rootJSON.get("properties.jcr__primaryType.name"))
        ).when().get(generateURL(primaryTypeSelf));

        // get the root primary type property definition and check that we're getting a property definition
        final String primaryTypeType = rootJSON.getString("properties.jcr__primaryType._links.type.href");
        expect().body(
                "type", equalTo("nt:propertyDefinition"),
                "properties.jcr__name.value", equalTo("jcr:primaryType")
        ).when().get(getURIByPath(primaryTypeType));
    }*/

    @Test
    public void testGetJCRSystem() {
        expect().statusCode(SC_OK)
                .contentType("application/json")
                .body(
                        "name", equalTo("jcr:system"),
                        "type", equalTo("rep:system")
                )
                .when().get(getURLByPath("jcr__system"));
    }

    private String generateURL(String path) {
        return target(path).getUri().toASCIIString();
    }

    private String getURLByPath(String path) {
        return generateURL("/api/byPath/" + path);
    }

    private String getURIById(String id) {
        return "/api/nodes/" + id;
    }

    private String getURIByPath(String path) {
        return "/api/byPath/" + URIUtils.escape(path);
    }

    private String getTypeURIByPath(String typeName) {
        return "/api/byPath/jcr__system/jcr__nodeTypes/" + URIUtils.escape(typeName);
    }

    private String getChildURI(String rootId, String childName) {
        return getURIById(rootId) + "/" + childName;
    }

    /*@Test
    public void testGetSite() {
        expect().statusCode(SC_OK)
                .contentType("application/json")
                .body(
                        "props.j__nodename.value", equalTo("site"),
                        "props.j__nodename.type", equalToIgnoringCase("string")
                )
                .when().get(generateURL("/sites/site"));
    }*/

    /*
    @Test
    public void createSite() {
        given().body("\"j__title\":\"mySite\"")
                .expect()
                .statusCode(SC_CREATED)
                .header(LOCATION, baseURL + "/sites/mySite")
                .body("j__nodename.value", equalTo("mySite"), "j__nodename.type", equalTo("string"),
                        "j__nodename.links.self", equalTo(baseURL + "/sites/mySite/props/j__nodename"))
                .when().put("/sites/mySite");
    }

    @Test
    public void attemptingToChangeAProtectedPropertyShouldFail(@ArquillianResource URL baseURL) {
        final String propURI = baseURL + "/sites/mySite/props/j__nodename";
        given().body("newSite")
                .expect()
                .statusCode(SC_METHOD_NOT_ALLOWED)
                .header(LOCATION, propURI)
                .header(ALLOW, "GET")
                .when().put(propURI);
    }*/

    private static class TestRepositoryFactory implements Factory<Repository> {
        static final Repository repository = new NoLoggingTransientRepository();
        @Override
        public Repository provide() {
            return repository;
        }

        @Override
        public void dispose(Repository instance) {
            // nothing
        }
    }

}