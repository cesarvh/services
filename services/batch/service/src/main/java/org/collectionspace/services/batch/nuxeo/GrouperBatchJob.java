package org.collectionspace.services.batch.nuxeo;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.collectionspace.services.client.CollectionSpaceClient;

import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.RelationClient;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.common.NuxeoBasedResource;
import org.collectionspace.services.common.api.RefNameUtils;
import org.collectionspace.services.common.api.RefNameUtils.AuthorityTermInfo;
import org.collectionspace.services.common.authorityref.AuthorityRefDocList;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.relation.RelationResource;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
import org.collectionspace.services.relation.RelationsCommonList;
import org.collectionspace.services.common.api.RefName;
import org.collectionspace.services.nuxeo.util.NuxeoUtils;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.collectionspace.services.batch.AbstractBatchInvocable;
import org.collectionspace.services.common.invocable.InvocationContext;
import org.collectionspace.services.client.CollectionSpaceClientUtils;

/**
  This batch job either creates a new group using the records passed in, or updates a group with those same records. Only list context is supported.

  If createNew is true, then a group with the name groupName will be created and the ListCSIDs will be related to it.
  If createNew is null/false, then the group with name name groupName will be updated to include the records in the ListCSIDs.

*/

public class GrouperBatchJob extends AbstractBatchJob {
  private final String GROUP_CLIENT = "groups"; // hardcoding here since import isn't working?
  private final String COLLECTIONOBJECT_DOCTYPE = "CollectionObject";
  private final String RELATION_TYPE = "affects"; 
  private final String RELATION_PREDICATE_DISP = "affects"; 
  private final String GROUP_DOCTYPE = "Group";
  final Logger logger = LoggerFactory.getLogger(GrouperBatchJob.class);

  public GrouperBatchJob() {
    setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_LIST));
  }


  @Override
  public void run() {
    setCompletionStatus(STATUS_MIN_PROGRESS);

    String mode = getInvocationContext().getMode();

    try { 
      // extract the params

      boolean createNew = false;
      String groupName = null;

      for (Param param : this.getParams()) {
        String key = param.getKey();

        if (key.equals("createNew")) {
          createNew = Boolean.parseBoolean(param.getValue());
        }  else if (key.equals("groupName")) {
          groupName = param.getValue();
        }
      }

      // if (mode.equalsIgnoreCase(INVOCATION_MODE_LIST)) {
        InvocationContext.ListCSIDs listWrapper = invocationCtx.getListCSIDs();
        List<String> listCsids = listWrapper.getCsid();
        // listCsids = this.getListCsids();
      // } 
      String groupCSID;

      if (createNew) {
        int groupCreationStatus = createGroup(groupName);
        groupCSID = results.getPrimaryURICreated();
      } else {
        groupCSID = "???";
      }
      int numberCreated = 0;
      for (String csid : listCsids) {
        if (createRelation(groupCSID, csid) == STATUS_ERROR) {
          break;
        } else {
          numberCreated += 1;
        }
      }

      if (completionStatus != STATUS_ERROR) {
        results.setNumAffected(numberCreated);
        results.setUserNote("GrouperBatchJob created or updated group with csid " + groupCSID + " and linked " + numberCreated + "collection object records.");
        setCompletionStatus(STATUS_COMPLETE);
      }

    } catch (Exception e) {
      completionStatus = STATUS_ERROR;
			errorInfo = new InvocationError(INT_ERROR_STATUS,
					"Grouper batch job had  a  problem creating relations: "+e.getLocalizedMessage());
			results.setUserNote(errorInfo.getMessage());
    }
  }

  public int createGroup(String groupName) {
    // first we create the group
    String groupPayload ="<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    +"<document name=\"groups\">"
      +"<ns2:groups_common xmlns:ns2=\"http://collectionspace.org/services/group\""
          +" xmlns:ns3=\"http://collectionspace.org/services/jaxb\">"
      +"<title>"+ groupName +"</title>"
    +"</ns2:groups_common></document>";

    NuxeoBasedResource resource = (NuxeoBasedResource) getResourceMap().get(GROUP_CLIENT);

    Response response = resource.create(getResourceMap(), null, groupPayload);

    if (response.getStatus() != CREATED_STATUS) {
      completionStatus = STATUS_ERROR;
      setCompletionStatus(STATUS_ERROR);
      setErrorInfo(new InvocationError(INT_ERROR_STATUS, "GrouperBatchJob: Problem greating new Group record"));
    } else {
      String newCSid = CollectionSpaceClientUtils.extractId(response);
      results.setPrimaryURICreated(newCSid);
    }
    return completionStatus;
  
  }

  public int createRelation(String groupCsid, String csid) {

		String relationPayload = "<document name=\"relations\">"
			+ "<ns2:relations_common xmlns:ns2=\"http://collectionspace.org/services/relation\"" 
			+ 		" xmlns:ns3=\"http://collectionspace.org/services/jaxb\">"
			+   "<subjectCsid>"+groupCsid+"</subjectCsid>"
			+   "<subjectDocumentType>"+ GROUP_DOCTYPE+"</subjectDocumentType>"
			+   "<objectCsid>"+csid+"</objectCsid>"
			+   "<objectDocumentType>"+ COLLECTIONOBJECT_DOCTYPE +"</objectDocumentType>"
			+   "<relationshipType>"+RELATION_TYPE+"</relationshipType>"
			+   "<predicateDisplayName>"+RELATION_PREDICATE_DISP+"</predicateDisplayName>"
			+ "</ns2:relations_common></document>";

      NuxeoBasedResource resource = (NuxeoBasedResource) getResourceMap().get(RelationClient.SERVICE_NAME);

      Response response = resource.create(getResourceMap(),null, relationPayload);

      if (response.getStatus() != CREATED_STATUS) {
        completionStatus = STATUS_ERROR;
        errorInfo = new InvocationError(INT_ERROR_STATUS, "GrouperBatchJob had a  problem creating a new relation");
        results.setUserNote(errorInfo.getMessage());
      }

    
    return completionStatus;
  }


}