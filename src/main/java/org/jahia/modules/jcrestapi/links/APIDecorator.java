/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2019 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.jcrestapi.links;

import org.jahia.modules.jcrestapi.API;
import org.jahia.modules.jcrestapi.SpringBeansAccess;
import org.jahia.modules.jcrestapi.URIUtils;
import org.jahia.modules.jcrestapi.json.APIObjectFactory;
import org.jahia.modules.json.*;
import org.jahia.modules.json.jcr.SessionAccess;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.version.Version;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Christophe Laprun
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class APIDecorator implements JSONDecorator<APIDecorator> {

    public static final String JCR__PROPERTY_DEFINITION = "jcr__propertyDefinition";

    private Map<String, JSONLink> links;
    private Map<String, JSONItem<? extends Item, APIDecorator>> references;

    private final boolean resolveReferences;
    private final boolean outputLinks;

    public APIDecorator() {
        this(API.shouldOutputLinks(), API.shouldResolveReferences());
    }

    public APIDecorator(boolean outputLinks, boolean resolveReferences) {
        this.outputLinks = outputLinks;
        this.resolveReferences = resolveReferences;
    }

    public APIDecorator(String uri) {
        this();

        initWith(uri);
    }

    @XmlElement
    public Map<String, JSONItem<? extends Item, APIDecorator>> getReferences() {
        return references != null ? Collections.unmodifiableMap(references) : null;
    }

    private Map<String, JSONLink> getLinks(boolean createIfNeeded) {
        if (links == null && createIfNeeded) {
            links = new HashMap<String, JSONLink>(7);
        }

        return links;
    }

    public String getURI() {
        final JSONLink link = getLink(API.SELF);
        return link != null ? link.getURIAsString() : null;
    }

    private void initWith(String uri) {
        addLink(JSONLink.createLink(API.ABSOLUTE, URIUtils.getAbsoluteURI(uri)));
        addLink(JSONLink.createLink(API.SELF, uri));
    }

    public void addLink(JSONLink link) {
        getLinks(true).put(link.getRel(), link);
    }

    protected JSONLink getLink(String relation) {
        return links != null ? links.get(relation) : null;
    }

    @XmlElement(name = "_links")
    public Map<String, JSONLink> getLinks() {
        return outputLinks ? Collections.unmodifiableMap(getLinks(false)) : null;
    }

    public void initFrom(JSONSubElementContainer<APIDecorator> container) {
        if (outputLinks) {
            final String uri = container.getParent().getDecorator().getURI();
            initWith(URIUtils.getChildURI(uri, container.getSubElementContainerName(), false));
            addLink(JSONLink.createLink(API.PARENT, uri));
        }
    }

    public <T extends Item> void initFrom(JSONItem<T, APIDecorator> jsonItem, T item) throws RepositoryException {
        if (outputLinks) {
            initWith(URIUtils.getURIFor(item));
            addLink(JSONLink.createLink(API.TYPE, URIUtils.getTypeURI(getTypeChildPath(jsonItem, item))));

            Node parent;
            try {
                parent = item.getParent();
            } catch (ItemNotFoundException e) {
                // expected when the item is root node, specify that parent is itself
                parent = (Node) item;
            }
            addLink(JSONLink.createLink(API.PARENT, URIUtils.getIdURI(parent.getIdentifier())));

            addLink(JSONLink.createLink(API.PATH, URIUtils.getURIFor(item, true)));
        }
    }

    private <T extends Item> String getTypeChildPath(JSONItem<T, APIDecorator> jsonItem, T item) throws RepositoryException {
        if (item instanceof Node) {
            return Names.escape(jsonItem.getUnescapedTypeName(item));
        } else {
            // get declaring node type
            final NodeType declaringNodeType = ((Property) item).getDefinition().getDeclaringNodeType();

            // get its name and escape it
            final String parentName = Names.escape(declaringNodeType.getName());

            // get its property definitions
            final PropertyDefinition[] parentPropDefs = declaringNodeType.getDeclaredPropertyDefinitions();
            final int numberOfPropertyDefinitions = parentPropDefs.length;

            // if we only have one property definition, we're done
            if (numberOfPropertyDefinitions == 1) {
                return URIUtils.getChildURI(parentName, JCR__PROPERTY_DEFINITION, false);
            } else {
                // we need to figure out which property definition matches ours in the array
                int index = 1; // JCR indexes start at 1
                for (int i = 0; i < numberOfPropertyDefinitions; i++) {
                    PropertyDefinition propDef = parentPropDefs[i];
                    if (propDef.getName().equals(item.getName())) {
                        index = i + 1; // adjust for start at 1 in JCR
                        break;
                    }
                }
                // create the indexed escaped link, if index = 1, no need for an index
                return URIUtils.getChildURI(parentName, Names.escape(JCR__PROPERTY_DEFINITION, index), false);
            }
        }
    }

    public void initFrom(JSONNode<APIDecorator> jsonNode) {
        if (outputLinks) {
            createAndAddLinkIfNeeded(jsonNode.getJSONProperties(), JSONConstants.PROPERTIES);
            createAndAddLinkIfNeeded(jsonNode.getJSONMixins(), JSONConstants.MIXINS);
            createAndAddLinkIfNeeded(jsonNode.getJSONChildren(), JSONConstants.CHILDREN);
            createAndAddLinkIfNeeded(jsonNode.getJSONVersions(), JSONConstants.VERSIONS);
        }
    }

    private void createAndAddLinkIfNeeded(JSONSubElementContainer<APIDecorator> container, String rel) {
        final String uri = container != null ? container.getDecorator().getURI() : null;
        if (uri != null) {
            addLink(JSONLink.createLink(rel, uri));
        }
    }

    @Override
    public APIDecorator newInstance() {
        return new APIDecorator(outputLinks, resolveReferences);
    }

    public void initFrom(JSONProperty jsonProperty) throws RepositoryException {
        if (outputLinks || resolveReferences) {
            final boolean reference = jsonProperty.isReference();
            if (reference) {
                if (jsonProperty.isMultiValued()) {
                    final String[] values = jsonProperty.getValueAsStringArray();
                    String[] links = null;
                    final int valuesNb = values.length;
                    if (valuesNb > 0) {
                        if (outputLinks) {
                            links = new String[valuesNb];
                        }

                        for (int i = 0; i < valuesNb; i++) {
                            final String val = values[i];
                            if (outputLinks) {
                                links[i] = getTargetLink(val, jsonProperty.isPath());
                            }
                            addReferencesIfNeeded(val);
                        }

                        if (outputLinks) {
                            addLink(JSONLink.createLink(API.TARGET, links));
                        }
                    }
                } else {
                    final String value = jsonProperty.getValueAsString();
                    if (outputLinks) {
                        addLink(JSONLink.createLink(API.TARGET, getTargetLink(value, jsonProperty.isPath())));
                    }
                    addReferencesIfNeeded(value);
                }

            }
        }
    }

    private void addReferencesIfNeeded(String value) throws RepositoryException {
        if (resolveReferences) {
            final Session session = SessionAccess.getCurrentSession().session;
            final Node node = session.getNodeByIdentifier(value);
            if (!SpringBeansAccess.getInstance().hasPermission("jcrestapi.references",node)) {
                return;
            }

            if (references == null) {
                references = new HashMap<String, JSONItem<? extends Item, APIDecorator>>(7);
            }

            references.put(node.getIdentifier(), APIObjectFactory.getInstance().createAPINode(node, Filter.OUTPUT_ALL, API.shouldIncludeFullChildren(), false, outputLinks));
        }
    }

    private String getTargetLink(String valueAsString, boolean path) throws RepositoryException {
        return path ? URIUtils.getByPathURI(valueAsString) : URIUtils.getIdURI(valueAsString);
    }

    public void initFrom(JSONMixin mixin) {
        if (outputLinks) {
            addLink(JSONLink.createLink(API.TYPE, URIUtils.getTypeURI(Names.escape(mixin.getType()))));
        }
    }

    public void initFrom(JSONVersion jsonVersion, Version version) throws RepositoryException {
        if (outputLinks) {
            final Version linearPredecessor = version.getLinearPredecessor();
            if (linearPredecessor != null) {
                addLink(JSONLink.createLink("previous", URIUtils.getURIFor(linearPredecessor)));
            }
            final Version linearSuccessor = version.getLinearSuccessor();
            if (linearSuccessor != null) {
                addLink(JSONLink.createLink("next", URIUtils.getURIFor(linearSuccessor)));
            }
            final Node frozenNode = version.getFrozenNode();
            if (frozenNode != null) {
                addLink(JSONLink.createLink(API.NODE_AT_VERSION, URIUtils.getURIFor(frozenNode)));
            }
        }
    }
}
