package org.collectionspace.services.batch.nuxeo;

import java.util.Arrays;
import java.util.HashMap;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.collectionspace.services.common.invocable.InvocationContext.ListCSIDs;
import org.collectionspace.services.common.invocable.InvocationContext;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.NuxeoBasedResource;

import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.PoxPayloadOut;

public class BulkObjectEditBatchJob extends  AbstractBatchJob {
  final Logger logger = LoggerFactory.getLogger(BulkObjectEditBatchJob.class);

  public BulkObjectEditBatchJob() {
    setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_LIST));
  }

  @Override
  public void run() {
    setCompletionStatus(STATUS_MIN_PROGRESS);
    try {
      InvocationContext ctx = getInvocationContext();

      String mode = ctx.getMode();


      if (mode.equalsIgnoreCase(INVOCATION_MODE_LIST)) {
        ArrayList<String> csids  = new ArrayList<String>();
        csids.addAll(ctx.getListCSIDs().getCsid());
        HashMap<String, String>  fieldsToValues = this.getValues();
        InvocationResults results = new InvocationResults();
        

        if (fieldsToValues.isEmpty()) {
          throw new Exception("There is nothing to update. Aborting...");
        }
        
        // setResults(updateRecords(csids, fieldsToValues));
        
        int NumAffected = 0 ;
        String payload = preparePayload(fieldsToValues);

        for (String csid : csids) {
          if (updateRecord(csid, payload) != -1) {
            NumAffected += 1;
          } else {
            // Make this more obvious?
            logger.warn("The recordw ith csid " +  csid + " was not updated");
          }
        }
        setCompletionStatus(STATUS_COMPLETE);

      } else {
        throw new Exception("Unsupported invocation mode " + mode);
      }
    } catch (Exception e) {
      setCompletionStatus(STATUS_ERROR);
      setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
    }
  }
  public String preparePayload(HashMap<String, String> fieldsToUpdate)  {
    /* Values that will require special handing:
      pahma: inventoryCount, pahmaFieldLocVerbatim, pahmaEthnographicFileCodeList
      ucbnh naturalhistory: taxon

      groups: We may have to merge these...
        - objectProductionPlaceGroupList > objectProductionPlaceGroup > objectProductionPlace
        - objectProductionPersonGroupList > objectProductionPersonGrou > objectProductionPerson
        - assocPeopleGroupList > assocPeopleGroup > assocPeople
        - taxonomicIdentGroupList > taxonomicIdentGroup > taxon
        - materialGroupList > materialGroup > material
        
      
    
    */ 


    String commonValues = "";
    String ucbnhValues = "";
    String pahmaValues = "";
    Boolean pahma = false;
    Boolean ucbnh = false;

    for (String key : fieldsToUpdate.keySet()) {
      String value = fieldsToUpdate.get(key);
      if (key.equals("objectProductionPlaceGroup"))  {
        commonValues += "<objectProductionPlaceGroupList><" + key + ">" + value + "<" + key + "/><objectProductionPlaceGroupList/>";
      } else if (key.equals("objectProductionPersonGroup")) {
        commonValues += "<objectProductionPersonGroupList><" + key + ">" + value + "<" + key + "/><objectProductionPersonGroupList/>";
      } else if (key.equals("assocPeopleGroup")) {
        commonValues += "<assocPeopleGroupList><" + key + ">" + value + "<" + key + "/><assocPeopleGroupList/>";
      } else if (key.equals("taxonomicIdentGroupList")) {
        ucbnhValues += "<taxonomicIdentGroup><" + key + ">" + value + "<" + key + "/><taxonomicIdentGroup/>";
        ucbnh = true;
      } else if (key.equals("materialGroup")) {
        commonValues += "<materialGroupList><" + key + ">" + value + "<" + key + "/><materialGroupList/>";
      } else if (key.equals("inventoryCount") || key.equals("pahmaFieldLocVerbatim") || key.equals("pahmaEthnographicFileCodeList")) {
        pahmaValues += "<" + key + ">" + value + "</" + key + ">";
        pahma = true;
      } else {
        commonValues += "<" + key + ">" + value + "</" + key + ">";
      }
    }

    String tenant = "";

    String commonPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
    "<document name=\"collectionobjects\">" +
      "<ns2:collectionobjects_common " +
      "xmlns:ns2=\"http://collectionspace.org/services/collectionobject\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + 
      commonValues +
    "</ns2:collectionobjects_common>";

    if (pahma) {
      commonPayload += "<ns2:collectionobjects_pahma " + 
                        "xmlns:ns2=\"http://collectionspace.org/services/collectionobject/local/pahma\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + pahmaValues + 
                        "</ns2:collectionobjects_pahma>";
    }
    if (ucbnh) {
      commonPayload += "<n2:collectionobjects_naturalhistory " +
                        "xmlns:ns2=\"http://collectionspace.org/services/collectionobject/domain/naturalhistory\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + ucbnhValues +
                        "</ns2:collectionobjects_naturalhistory>";
    }



    commonPayload += "</document>";
    return commonPayload;
  }


  public int updateRecord(String csid, String payload) throws URISyntaxException {
    int result = 0;

    ResourceMap resource = getResourceMap();
    NuxeoBasedResource collectionObjectResource = (NuxeoBasedResource) resource.get(CollectionObjectClient.SERVICE_NAME);

    String response = new String (collectionObjectResource.update(getServiceContext(), resource, createUriInfo(), csid, payload));

    if (response != "200") {
      result = -1;
    }

    return result;

  }

  /*
   * @return a HashMap containing (K, V) pairs of  (Field, NewValue)
   */
  public HashMap<String, String>  getValues() {
    HashMap<String, String> results = new HashMap<String,  String>();
    for(Param param : this.getParams()) {
      if (param.getKey() != null) {
        String val = param.getValue();
        if (val != null && !val.equals("")) {
          results.put(param.getKey(), param.getValue());
        }
      }
    }
    return results;
  }
}