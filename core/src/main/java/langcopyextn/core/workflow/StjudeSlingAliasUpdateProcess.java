package langcopyextn.core.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.collection.ResourceCollectionManager;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Iterator;

@Component(
        immediate = true,
        service = WorkflowProcess.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Workflow to add sling:alias to jcr:content adding SPANISH ",
                "process.label=" + "St. Jude Language Copy Sling Alias Extension Process"
        }
)
public class StjudeSlingAliasUpdateProcess extends AbstractLangCopyProcess implements WorkflowProcess {

    public static final String JCR_TITLE = "jcr:title";
    public static final String SPANISH_LANG_CODE = "es-us";
    public static final String SPANISH_ELL_PREFIX = " - ";

    public static final String CONTENT_WEB_SITES_REFERENCE_BASE = "/content/web-sites-reference/";

    @Reference
    protected ResourceCollectionManager resourceCollectionManager;
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected void createLangCopyWorkflow(Resource targetResource, String language, boolean deep) {
        setDeepChildPageSlingAlias(targetResource, language, deep);
        if (!targetResource.getPath().startsWith(CONTENT_WEB_SITES_REFERENCE_BASE)) {
            setParentPageSlingAlias(targetResource, language);
        }
    }

    @Override
    protected void updateLangCopyWorkflow(Resource targetResource, String language, boolean deep) {
        createLangCopyWorkflow(targetResource, language, deep);
    }


    /**
     * To set the Deep Child pages sling Alias in target path
     *
     * @param resource   Resource to set the sling:alias
     * @param targetLang targetLang code
     * @param deep       consider child pages or not
     */
    private void setDeepChildPageSlingAlias(Resource resource, String targetLang, boolean deep) {
        Iterator<Resource> iterator = resource.getChildren().iterator();
        while (iterator.hasNext()) {
            Resource childRes = iterator.next();
            if (childRes.getPath().startsWith(CONTENT_WEB_SITES_REFERENCE_BASE)) {
                if (childRes.getName().equals(JcrConstants.JCR_CONTENT)) {
                    ValueMap vm = childRes.adaptTo(ValueMap.class);
                    String title = vm.get(JCR_TITLE, String.class);
                    //vm.put(JCR_TITLE, targetLang + SPANISH_ELL_PREFIX + title);
                }
            }
            if (childRes.getName().equals(JcrConstants.JCR_CONTENT)) {
                if (!childRes.getPath().startsWith(CONTENT_WEB_SITES_REFERENCE_BASE)) {
                    ModifiableValueMap vm = childRes.adaptTo(ModifiableValueMap.class);
                    vm.put(SLING_ALIAS, resource.getName());
                    vm.put("jcr:title","SDL-- "+vm.get("jcr:title"));
                }
            } else if (deep == true) {
                setDeepChildPageSlingAlias(childRes, targetLang, deep);
            }
        }
    }

    /**
     * To set the parent page Sling Alias , this is usefull if only child page added in the copy but parent page copied
     * sling alias is not getting added .
     *
     * @param resource resource to set the parent page sling alias if not found
     */
    private void setParentPageSlingAlias(Resource resource, String targetLang) {
        Resource parent = resource.getParent();
        if (parent.getPath().endsWith(targetLang)) {
            return;
        } else {
            Resource childRes = parent.getChild(JcrConstants.JCR_CONTENT);
            if (childRes != null) {
                ModifiableValueMap valueMap = childRes.adaptTo(ModifiableValueMap.class);
                if (valueMap.get(SLING_ALIAS) == null) {
                    valueMap.put(SLING_ALIAS, parent.getName());
                }
                setParentPageSlingAlias(parent, targetLang);
            }
        }
    }

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
            throws WorkflowException {
        super.resolverFactory = this.resolverFactory;
        super.resourceCollectionManager = resourceCollectionManager;
        executeStart(workItem, workflowSession, metaDataMap);
    }
}
