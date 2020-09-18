package langcopyextn.core.util;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.collection.ResourceCollection;
import com.adobe.granite.workflow.collection.ResourceCollectionManager;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.adobe.granite.workflow.PayloadMap.TYPE_JCR_PATH;
import static com.adobe.granite.workflow.PayloadMap.TYPE_JCR_UUID;

public class WorkflowUtils {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowUtils.class);

    /**
     * Helper method to retrieve resourceResolver.
     * @param resolverFactory - the current resource resolver factory
     * @param session - the property to be set in workItem
     * @return resourceResolver resourceResolver instance
     * @throws LoginException - LoginException
     */
    public static ResourceResolver getResourceResolver(ResourceResolverFactory resolverFactory,
                                                       Session session) throws LoginException {
        return resolverFactory.getResourceResolver(Collections.singletonMap(
                JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session));
    }

    /**
     * Takes the current payload path of the workflow and determines if it is a page, asset, or workflow package.
     * If it is a workflow package it returns all of the contents of that package for processing,
     * otherwise, it returns the original payload path.
     * @param workflowPayloadPath - current workflow's payload path
     * @param session - current jcr session
     * @param rcm - ResourceCollectionManager based on current session
     * @return - list of pages or assets to process
     * @throws RepositoryException - RepositoryException
     * @throws WorkflowException - WorkflowException
     */
    public static List<String> getPayloadPathsToProcess(String workflowPayloadPath, Session session,
                                                        ResourceCollectionManager rcm)
            throws RepositoryException, WorkflowException {
        List<String> paths = new ArrayList<>();
        if (workflowPayloadPath != null) {
            // check for resource collection (workflow package)
            List<ResourceCollection> rcCollections =
                    rcm.getCollectionsForNode((Node) session.getItem(workflowPayloadPath));
            ResourceCollection rcCollection = null;
            if (null != rcCollections && !rcCollections.isEmpty()) {
                //Only support a single workflow package
                rcCollection = rcCollections.get(0);
            }
            // get list of paths to replicate (no resource collection: size == 1)
            // otherwise size >= 1
            paths = getWorkflowPackagePaths(workflowPayloadPath, rcCollection);
        } else {
            LOG.warn("Cannot process because path is null");
        }

        return paths;
    }

    /**
     * Checks payload type and returns valid payload path (if the item exists)
     * @param data - WorkflowData obj
     * @param session - Current Session
     * @return - valid payload path
     * @throws RepositoryException - item could not be read
     */
    public static String getPayloadPath(WorkflowData data, Session session) throws RepositoryException {
        String payloadPath = null;
        String type = data.getPayloadType();
        //Get the payload path
        if (type.equals(TYPE_JCR_PATH) && data.getPayload() != null) {
            String payloadData = (String) data.getPayload();
            if (session.itemExists(payloadData)) {
                payloadPath = payloadData;
            }
        } else if (data.getPayload() != null && type.equals(TYPE_JCR_UUID)) {
            Node node = session.getNodeByIdentifier((String) data.getPayload());
            payloadPath = node.getPath();
        }

        return payloadPath;
    }

    /**
     * Takes the current workflow's workitem and returns the initiator's ID
     * @param workItem - current workitem
     * @return - initiator user id
     */
    public static String getWorkflowInitiator(WorkItem workItem) {
        return workItem.getWorkflow().getInitiator();
    }

    private static List<String> getWorkflowPackagePaths(String path, ResourceCollection rcCollection)
            throws WorkflowException {
        List<String> paths = new ArrayList<>();
        if (rcCollection == null) {
            paths.add(path);
        } else {
            LOG.debug("ResourceCollection detected " + rcCollection.getPath());
            // this is a resource collection. the collection itself is not to be processed
            // only its members
            try {
                //Interested in assets and pages only
                List<Node> members = rcCollection.list(new String[]{"cq:Page", "dam:Asset"});
                for (Node member : members) {
                    String memberPath = member.getPath();
                    paths.add(memberPath);
                }
            } catch (RepositoryException re) {
                LOG.error("Cannot build path list out of the resource collection " + rcCollection.getPath());
                throw new WorkflowException("Cannot build path list out of the resource collection "
                        + rcCollection.getPath());
            }
        }
        return paths;
    }
}
