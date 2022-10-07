package it.smartcommunitylab.aac.internal.templates;

import java.util.Arrays;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.templates.model.FixedTemplateModel;

public class InternalChangeAccountTemplate extends FixedTemplateModel {
    public static final String TEMPLATE = "changeaccount";
    private static final String[] KEYS = { "changeaccount.text" };

    public InternalChangeAccountTemplate(String realm) {
        super(SystemKeys.AUTHORITY_INTERNAL, realm, null, TEMPLATE, Arrays.asList(KEYS));
    }

}
