/*
 * Asset Share Commons
 *
 * Copyright (C) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.aem.commons.assetshare.content.renditions.impl.dispatchers;

import com.adobe.acs.commons.util.PathInfoUtil;
import com.adobe.aem.commons.assetshare.content.renditions.AssetRenditionDispatcher;
import com.adobe.aem.commons.assetshare.content.renditions.AssetRenditionParameters;
import com.adobe.aem.commons.assetshare.content.renditions.AssetRenditions;
import com.adobe.aem.commons.assetshare.util.impl.ExtensionOverrideRequestWrapper;
import com.day.cq.commons.PathInfo;
import com.day.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.osgi.framework.Constants.SERVICE_RANKING;

@Component(
        property = {
                SERVICE_RANKING + ":Integer=" + -10000,
                "webconsole.configurationFactory.nameHint={name} [ {label} ] @ {service.ranking}"
        }
)
@Designate(
        ocd = InternalRedirectRenditionDispatcherImpl.Cfg.class,
        factory = true
)
public class InternalRedirectRenditionDispatcherImpl implements AssetRenditionDispatcher {
    private static Logger log = LoggerFactory.getLogger(InternalRedirectRenditionDispatcherImpl.class);

    private static final String OSGI_PROPERTY_VALUE_DELIMITER = "=";

    private Cfg cfg;

    private ConcurrentHashMap<String, String> mappings;

    @Reference
    private AssetRenditions assetRenditions;

    @Override
    public String getLabel() {
        return cfg.label();
    }

    @Override
    public String getName() {
        return cfg.name();
    }

    @Override
    public Map<String, String> getOptions() {
        return assetRenditions.getOptions(mappings);
    }

    @Override
    public boolean isHidden() {
        return cfg.hidden();
    }

    @Override
    public Set<String> getRenditionNames() {
        return mappings.keySet();
    }

    @Override
    public List<String> getTypes() {
        if (cfg.types() != null) {
            return Arrays.asList(cfg.types());
        }

        return Collections.EMPTY_LIST;
    }

    @Override
    public void dispatch(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException, ServletException {
        final AssetRenditionParameters parameters = new AssetRenditionParameters(request);

        final String expression = mappings.get(parameters.getRenditionName());

        if (StringUtils.isNotBlank(expression)) {
            final String evaluatedExpression = assetRenditions.evaluateExpression(request, expression);
            final PathInfo pathInfo = new PathInfo(request.getResourceResolver(), evaluatedExpression);

            log.debug("Serving internal redirect rendition [ {} ] for resolved rendition name [ {} ]",
                    evaluatedExpression,
                    parameters.getRenditionName());

            final RequestDispatcherOptions options = new RequestDispatcherOptions();
            final String resourcePath = Text.unescape(pathInfo.getResourcePath());

            options.setReplaceSelectors(StringUtils.removeStart(pathInfo.getSelectorString(), "."));
            options.setReplaceSuffix(pathInfo.getSuffix());

            request.getRequestDispatcher(resourcePath, options)
                    .include(new ExtensionOverrideRequestWrapper(request, pathInfo.getExtension()), response);

        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not serve asset rendition.");
        }
    }

    @Activate
    protected void activate(Cfg cfg) {
        this.cfg = cfg;

        this.mappings = new ConcurrentHashMap<>();

        if (this.cfg.rendition_mappings() != null) {
            Arrays.stream(this.cfg.rendition_mappings())
                    .map(mapping -> StringUtils.split(mapping, OSGI_PROPERTY_VALUE_DELIMITER))
                    .filter(segments -> segments.length == 2)
                    .filter(segments -> StringUtils.isNotBlank(segments[0]))
                    .filter(segments -> StringUtils.isNotBlank(segments[1]))
                    .forEach(segments ->
                            mappings.put(StringUtils.strip(segments[0]), StringUtils.strip(segments[1])));
        }
    }

    @ObjectClassDefinition(name = "Asset Share Commons - Rendition Dispatcher - Internal Redirect Renditions")
    public @interface Cfg {
        @AttributeDefinition
        String webconsole_configurationFactory_nameHint() default "{name} [ {label} ] @ {service.ranking}";

        @AttributeDefinition(
                name = "Name",
                description = "The system name of this Rendition Dispatcher. This should be unique across all AssetRenditionDispatcher instances."
        )
        String name() default "internal-redirect";

        @AttributeDefinition(
                name = "Label",
                description = "The human-friendly name of this AssetRenditionDispatcher and may be displayed to authors."
        )
        String label() default "Internal Redirect Renditions";

        @AttributeDefinition(
                name = "Rendition types",
                description = "The types of renditions this configuration will return. Ideally all renditions in this configuration apply types specified here. This is used to drive and scope the Asset Renditions displays in Authoring datasources. OOTB types are: `image` and `video`"
        )
        String[] types() default {};

        @AttributeDefinition(
                name = "Hide renditions",
                description = "Hide if this AssetRenditionDispatcher configuration is not intended to be exposed to AEM authors for selection in dialogs."
        )
        boolean hidden() default false;

        @AttributeDefinition(
                name = "Rendition mappings",
                description = "In the form: <renditionName>" + OSGI_PROPERTY_VALUE_DELIMITER + "<postFix>"
        )
        String[] rendition_mappings() default {};

        @AttributeDefinition(
                name = "Service ranking",
                description = "The larger the number, the higher the precedence."
        )
        int service_ranking() default 0;
    }
}