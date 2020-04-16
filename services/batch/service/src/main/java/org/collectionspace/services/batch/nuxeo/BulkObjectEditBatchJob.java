package org.collectionspace.services.batch.nuxeo;

import java.util.Arrays;
import java.util.HashMap;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.collectionspace.services.common.invocable.InvocationContext.ListCSIDs;
import org.collectionspace.services.common.invocable.InvocationContext;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.NuxeoBasedResource;

import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;

import org.dom4j.DocumentException;
import org.dom4j.Element;


/**
 * A batch job that updates the following fields:
 * collectionobject_common: numberOfObjects, numberValue, material, fieldCollectionPlace, responsibleDepartment, assocPeople, numberType, objectProductionPerson, objectProductionPlace, fieldCollector, objectStatus, contentPlace, objectName
 * collectionobject_naturalistory: taxon
 * collectionobject_pahma: pahmaEthnographicFileCodeList, pahmaFieldLocVerbatim, inventoryCount
 * The list contexts is
 *
 *
 * The following parameters are allowed:
 *
 * targetCSID: csid of target records, a dictionary of parameters (fields to update) and their new values 
 *
 * @author Cesar Villalobos
 */

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
        
        int numAffected = 0 ;
        String payload = preparePayload(fieldsToValues);

        for (String csid : csids) {
          String mergedPayload = mergePayloads(csid, new PoxPayloadOut(payload.getBytes()));

          if (mergedPayload != null) {
            if(updateRecord(csid, mergedPayload) != -1) {
              numAffected += 1;
            } else {
              logger.warn("The record with csid " +  csid + " was not updated");
            }
          }
        }
        setCompletionStatus(STATUS_COMPLETE);
        results.setNumAffected(numAffected);
        results.setUserNote("Updated " + numAffected + " records with the payload " + payload);

        setResults(results);
      } else {
        throw new Exception("Unsupported invocation mode " + mode);
      }
    } catch (Exception e) {
      setCompletionStatus(STATUS_ERROR);
      setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
    }
  }
  public String preparePayload(HashMap<String, String> fieldsToUpdate)  {

    String commonValues = "";
    String ucbnhValues = "";
    String pahmaValues = "";
    Boolean pahma = false;
    Boolean ucbnh = false;

    String otherNumber = "<otherNumberList><otherNumber>";
    Boolean otherNumFlag = false;

    for (String key : fieldsToUpdate.keySet()) {
      String value = fieldsToUpdate.get(key);
      
      // Temporary workaround until we're able to pass in full repeated groups and lists as a payload, build the payload on the fly
      if (key.equals("material")) {
        commonValues += "<materialGroupList><materialGroup><" + key + ">" + value + "</" + key + "></materialGroup></materialGroupList>";
      } else if (key.equals("responsibleDepartment")) {
        commonValues += "<responsibleDepartments><" + key + ">" + value + "</" + key + "></responsibleDepartments>";
      } else if (key.equals("assocPeople")) {
        commonValues += "<assocPeopleGroupList><assocPeopleGroup><" + key + ">" + value + "</" + key + "></assocPeopleGroup></assocPeopleGroupList>";
      } else if (key.equals("objectProductionPerson")) {
        commonValues += "<objectProductionPersonGroupList><objectProductionPersonGroup><" + key + ">" + value + "</" + key + "></objectProductionPersonGroup></objectProductionPersonGroupList>";
      } else if (key.equals("objectProductionPlace")) {
        commonValues += "<objectProductionPlaceGroupList><objectProductionPlaceGroup><" + key + ">" + value + "</" + key + "></objectProductionPlaceGroup></objectProductionPlaceGroupList>";
      } else if (key.equals("fieldCollector")) {
        commonValues += "<fieldCollectors><" + key + ">" + value + "</" + key + "></fieldCollectors>";
      } else if (key.equals("objectStatus")) {
        commonValues += "<objectStatusList><" + key + ">" + value + "</" + key + "></objectStatusList>";
      } else if (key.equals("contentPlace")) {
        commonValues += "<contentPlaces><" + key + ">" + value + "</" + key + "></contentPlaces>";
      } else if (key.equals("objectName")) {
        commonValues += "<objectNameList><objectNameGroup><" + key + ">" + value + "</" + key + "></objectNameGroup></objectNameList>";
      } else if (key.equals("briefDescription")) {
        commonValues += "<briefDescriptions><" + key + ">" + value + "</" + key + "></briefDescriptions>";
      } else if (key.equals("numberValue") || key.equals("numberType")) {
        otherNumber += "<" + key + ">" + value + "</" + key + ">";
        otherNumFlag = true;
      } else if (key.equals("taxon")) {
        ucbnhValues += "<taxonomicIdentGroupList><taxonomicIdentGroup><" + key + ">" + value + "<" + key + "/></taxonomicIdentGroupList></taxonomicIdentGroup>";
        ucbnh = true;
      } else if (key.equals("inventoryCount") || key.equals("pahmaFieldLocVerbatim")) {
        pahmaValues += "<" + key + ">" + value + "</" + key + ">";
        pahma = true;
      } else if (key.equals("pahmaEthnographicFileCode")) {
        pahmaValues += "<pahmaEthnographicFileCodeList><" + key + ">" + value + "</" + key + "></pahmaEthnographicFileCode>";
        pahma = true;
      } else {
        commonValues += "<" + key + ">" + value + "</" + key + ">";
      }
    }

    if (otherNumFlag) {
      otherNumber += "</otherNumber></otherNumberList>";
      commonValues += otherNumber;
    }

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

  public String mergePayloads(String csid, PoxPayloadOut batchPayload) throws Exception {
    // now we have the bytes for both t
    HashMap<String, Element> batchElementList = new HashMap<String, Element>();
    PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(csid);

    // Now we create a map of each part -> field mappings (_common, _ucbnh, _pahma)
    for (PayloadOutputPart batchCandidatePart : batchPayload.getParts()) {
      Element batchCandidatePartElement = batchCandidatePart.asElement();
      batchElementList.put(batchCandidatePartElement.getName(), batchCandidatePartElement);
    }

    // now, we can iterate through each part-> field map
    for (String batchPartElement : batchElementList.keySet()) {

      // grab the same part from the collection object as the payload part
      Element objectPartElement = collectionObjectPayload.getPart(batchPartElement).asElement();

      Element batchElement = batchElementList.get(batchPartElement);

      // Now we iterate through each field of this part
      for (Element batchElementField : (List<Element>) batchElement.elements()) {
        String childElemName = batchElementField.getName(); // Now we need to find the matching element in the collection object

        if (childElemName == null) {
          continue;
        }

        Element objectElementField = objectPartElement.element(childElemName);

        // If it is a repeated field, we will now merge the batch and the payloads
        for (Element objElem : (List<Element>) objectElementField.elements()) {
          if (!objElem.getText().equals("")) {
            batchElementField.add(objElem.createCopy());
          }
        }

        // If its not repeateable, we simply remove and replace
        objectPartElement.remove(objectElementField);
        objectPartElement.add(batchElementField.createCopy());

      }

    }

    return collectionObjectPayload.asXML();
  }
  
  public int updateRecord(String csid, String payload) throws URISyntaxException, DocumentException {
    int result = 0;

    try {
      ResourceMap resource = getResourceMap();
      NuxeoBasedResource collectionObjectResource = (NuxeoBasedResource) resource.get(CollectionObjectClient.SERVICE_NAME);
      String response = new String(collectionObjectResource.update(getServiceContext(), resource, createUriInfo(), csid, payload));
    } catch (Exception e) {
      result = -1;
    }
    return result;
  }

  /*
   * @return a HashMap containing (K, V) pairs of  (Field, NewValue)
   */
  public HashMap<String, String>  getValues() {
    HashMap<String, String> results = new HashMap<String,  String>();
    for (Param param : this.getParams()) {
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
