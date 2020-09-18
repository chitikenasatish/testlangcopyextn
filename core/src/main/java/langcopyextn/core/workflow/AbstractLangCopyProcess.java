package langcopyextn.core.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.collection.ResourceCollectionManager;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import langcopyextn.core.util.WorkflowUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.jcr.Session;
import java.util.List;

public abstract class AbstractLangCopyProcess {

    public static final String DAM_CREATE_LANG_COPY_WORKFLOW
            = "/var/workflow/models/dam/dam-create-language-copy";
    public static final String DAM_UPDATE_LANG_COPY_WORKFLOW
            = "/var/workflow/models/dam/dam-update-language-copy";
    public static final String CREATE_LANG_COPY_WORKFLOW
            = "/var/workflow/models/wcm-translation/create_language_copy";
    public static final String UPDATE_LANG_COPY_WORKFLOW
            = "/var/workflow/models/wcm-translation/update_language_copy";

    public static final String SOURCE_EN_LANG = "/en/";
    public static final String SOURCE_ELL_LANG = "/en_us/";

    public static final String FORWARD_SLASH = "/";

    public static final String SLING_ALIAS = "sling:alias";
    public static final String CONTENT = "/content";
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractLangCopyProcess.class);
    protected final ThreadLocal<Session> session = new ThreadLocal<Session>();
    protected final ThreadLocal<ResourceResolver> resourceResolver = new ThreadLocal<ResourceResolver>();
    protected final ThreadLocal<WorkflowSession> wfSession = new ThreadLocal<>();
    protected String workflowInitiator;

    protected ResourceResolverFactory resolverFactory;


    protected ResourceCollectionManager resourceCollectionManager;

    /**
     * Execute workflow step
     *
     * @param workItem          workitem
     * @param workflowSession   session workflow
     * @param parentMetaDataMap metadataMap
     * @throws WorkflowException exception is there is error
     */
    public void executeStart(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap parentMetaDataMap)
            throws WorkflowException {
        try {

            wfSession.set(workflowSession);
            session.set(workflowSession.adaptTo(Session.class));
            if (null == session.get()) {
                return;
            }
            workflowInitiator = WorkflowUtils.getWorkflowInitiator(workItem);
            WorkflowData data = workItem.getWorkflowData();
            resourceResolver.set(WorkflowUtils.getResourceResolver(resolverFactory, session.get()));
            String workflowId = workItem.getWorkflow().getWorkflowModel().getId();
            MetaDataMap metaDataMap = workItem.getWorkflow().getMetaDataMap();
            String deep = metaDataMap.get("deep", String.class);

            //I am not sure even though it is workflow payload comes as . if we create lang copy from Menu .Handling all
            if (".".equals(workItem.getWorkflowData().getPayload())) {
                String[] srcPathList = metaDataMap.get("srcPathList", String.class).split(",");
                String[] languageList = metaDataMap.get("languageList", String.class).split(",");
                if (workflowId.equals(CREATE_LANG_COPY_WORKFLOW)) {
                    for (String path : srcPathList) {
                        Resource targetResource = null;
                        for (String language : languageList) {
                            LOG.debug("************Payload Path {} ***************  LANG :  {}", path, language);
                            String targetPath = path.replace(SOURCE_EN_LANG,
                                    FORWARD_SLASH + language + FORWARD_SLASH);
                            // make sure to replace ELL path as there is differnce in the code for ELL it is en_us/en-us
                            targetPath = targetPath.replace(SOURCE_ELL_LANG,
                                    FORWARD_SLASH + language + FORWARD_SLASH);

                            targetResource = resourceResolver.get().resolve(targetPath);
                            if (null != targetResource) {
                                if (targetResource != null) {
                                    createLangCopyWorkflow(targetResource, language, "true".equals(deep));
                                }
                            }
                        }
                    }
                }
            } else if (workItem.getWorkflowData().getPayload() != null) {
                String payloadPath = WorkflowUtils.getPayloadPath(data, session.get());
                List<String> paths = WorkflowUtils.getPayloadPathsToProcess(payloadPath,
                        session.get(), resourceCollectionManager);
                LOG.debug("************Payload Path *************** " + payloadPath);

                for (String path : paths) {
                    Resource targetResource = null;
                    String language = null;
                    if (workflowId.equals(CREATE_LANG_COPY_WORKFLOW)) {
                        language = metaDataMap.get("language", String.class);
                        String targetPath = path.replace(SOURCE_EN_LANG,
                                FORWARD_SLASH + language + FORWARD_SLASH);
                        targetResource = resourceResolver.get().resolve(targetPath);
                        createLangCopyWorkflow(targetResource, language, "true".equals(deep));
                    } else if (workflowId.equals(UPDATE_LANG_COPY_WORKFLOW)) {
                        //It is update Language workflow
                        language = metaDataMap.get("destinationLanguage", String.class);
                        String launchRootResourcePath = data
                                .getMetaDataMap().get("launchRootResourcePath", null);
                        if (launchRootResourcePath == null) {
                            launchRootResourcePath = data.getMetaDataMap().get("launchRootResourcePathList",
                                    null);
                        }
                        if (launchRootResourcePath != null) {
                            String[] values = launchRootResourcePath.contains(",")
                                    ? launchRootResourcePath.split(",")
                                    : launchRootResourcePath.split(";");
                            for (String value : values) {
                                targetResource = resourceResolver.get()
                                        .resolve(value + path);
                                updateLangCopyWorkflow(targetResource, language, "true".equals(deep));
                            }

                        } else {
                            targetResource = resourceResolver.get().resolve(path);
                            updateLangCopyWorkflow(targetResource, language, "true".equals(deep));
                        }

                    }

                }

            } else {
                LOG.warn("Cannot process because path is null for this " + "workitem: " + workItem.toString());
            }
        } catch (Exception e) {
            LOG.error("Exception in executing workflow step : {}", e);
            throw new WorkflowException("Exception in executing workflow step", e);
        }
    }

    protected abstract void createLangCopyWorkflow(Resource targetResource, String language, boolean deep);

    protected abstract void updateLangCopyWorkflow(Resource targetResource, String language, boolean deep);


}
