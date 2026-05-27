package com.opstool.service;

import com.opstool.model.Credential;
import com.opstool.model.ScheduledTask;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists tasks and credentials as XML.
 *
 * Credential storage layout (per-user files):
 *   <dataDir>/creds_<username>.xml
 *
 * Each file holds ONE credential entry for that username.
 * The password is stored as plain text. When the ops team enters a username
 * in the Task Dialog, the matching creds_<username>.xml is loaded to supply
 * the password automatically.
 *
 * Task storage:
 *   <dataDir>/tasks.xml
 */
public class XmlStorageService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final File dataDir;
    private final File taskFile;

    public XmlStorageService(String dataDirPath) {
        this.dataDir  = new File(dataDirPath);
        this.dataDir.mkdirs();
        this.taskFile = new File(dataDir, "tasks.xml");
    }

    // ─── Per-user credential file helpers ───────────────────────────────────

    /** Returns the creds_<username>.xml file for the given username. */
    public File credFileForUser(String username) {
        // Sanitise username so it is safe as a filename
        String safe = username.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        return new File(dataDir, "creds_" + safe + ".xml");
    }

    /**
     * Look up credentials by username.
     * Reads creds_<username>.xml and returns the Credential, or null if not found.
     */
    public Credential loadCredentialByUsername(String username) {
        if (username == null || username.isEmpty()) return null;
        File f = credFileForUser(username);
        if (!f.exists()) return null;
        try {
            Document doc = parseXml(f);
            NodeList nodes = doc.getElementsByTagName("credential");
            if (nodes.getLength() == 0) return null;
            return elementToCredential((Element) nodes.item(0));
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Save a credential into its per-user XML file (creds_<username>.xml).
     * One file = one user; calling save replaces the existing entry.
     */
    public void saveCredential(Credential cred) {
        if (cred.getId() == null || cred.getId().isEmpty()) {
            cred.setId(UUID.randomUUID().toString());
        }
        try {
            Document doc = newDoc("credentials");
            Element root = doc.getDocumentElement();
            root.appendChild(credentialToElement(doc, cred));
            writeXml(doc, credFileForUser(cred.getUsername()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Delete the per-user creds file for the given username. */
    public void deleteCredential(String username) {
        File f = credFileForUser(username);
        if (f.exists()) f.delete();
    }

    /**
     * List every credential stored (one per user).
     * Scans the data directory for creds_*.xml files.
     */
    public List<Credential> loadAllCredentials() {
        List<Credential> list = new ArrayList<>();
        File[] files = dataDir.listFiles(
            (dir, name) -> name.startsWith("creds_") && name.endsWith(".xml"));
        if (files == null) return list;
        for (File f : files) {
            try {
                Document doc = parseXml(f);
                NodeList nodes = doc.getElementsByTagName("credential");
                for (int i = 0; i < nodes.getLength(); i++) {
                    list.add(elementToCredential((Element) nodes.item(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return list;
    }

    // ─── Tasks ───────────────────────────────────────────────────────────────

    public List<ScheduledTask> loadTasks() {
        List<ScheduledTask> list = new ArrayList<>();
        if (!taskFile.exists()) return list;
        try {
            Document doc = parseXml(taskFile);
            NodeList nodes = doc.getElementsByTagName("task");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                ScheduledTask t = new ScheduledTask();
                t.setId(attr(e, "id"));
                t.setName(child(e, "name"));
                t.setTaskType(ScheduledTask.TaskType.valueOf(child(e, "taskType")));
                t.setStatus(ScheduledTask.TaskStatus.valueOf(child(e, "status")));
                t.setSourceCredentialId(child(e, "sourceCredentialId"));
                t.setTargetCredentialId(child(e, "targetCredentialId"));
                t.setSourcePath(child(e, "sourcePath"));
                t.setTargetPath(child(e, "targetPath"));
                t.setServiceName(child(e, "serviceName"));
                t.setScheduleType(ScheduledTask.ScheduleType.valueOf(child(e, "scheduleType")));
                String sat = child(e, "scheduledAt");
                if (sat != null && !sat.isEmpty()) t.setScheduledAt(LocalDateTime.parse(sat, DT_FMT));
                String interval = child(e, "intervalMinutes");
                if (interval != null && !interval.isEmpty()) t.setIntervalMinutes(Integer.parseInt(interval));
                t.setCronExpression(child(e, "cronExpression"));
                String lastRun = child(e, "lastRunAt");
                if (lastRun != null && !lastRun.isEmpty()) t.setLastRunAt(LocalDateTime.parse(lastRun, DT_FMT));
                t.setLastRunResult(child(e, "lastRunResult"));
                String created = child(e, "createdAt");
                if (created != null && !created.isEmpty()) t.setCreatedAt(LocalDateTime.parse(created, DT_FMT));
                // New fields added for simplified ops workflow
                t.setTargetUsername(child(e, "targetUsername"));
                list.add(t);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public void saveTask(ScheduledTask task) {
        if (task.getId() == null || task.getId().isEmpty()) {
            task.setId(UUID.randomUUID().toString());
        }
        List<ScheduledTask> list = loadTasks();
        list.removeIf(t -> t.getId().equals(task.getId()));
        list.add(task);
        writeTasks(list);
    }

    public void deleteTask(String id) {
        List<ScheduledTask> list = loadTasks();
        list.removeIf(t -> t.getId().equals(id));
        writeTasks(list);
    }

    private void writeTasks(List<ScheduledTask> list) {
        try {
            Document doc = newDoc("tasks");
            Element root = doc.getDocumentElement();
            for (ScheduledTask t : list) {
                Element e = doc.createElement("task");
                e.setAttribute("id", t.getId());
                addChild(doc, e, "name",               t.getName());
                addChild(doc, e, "taskType",           t.getTaskType().name());
                addChild(doc, e, "status",             t.getStatus().name());
                addChild(doc, e, "sourceCredentialId", t.getSourceCredentialId());
                addChild(doc, e, "targetCredentialId", t.getTargetCredentialId());
                addChild(doc, e, "targetUsername",     t.getTargetUsername());
                addChild(doc, e, "sourcePath",         t.getSourcePath());
                addChild(doc, e, "targetPath",         t.getTargetPath());
                addChild(doc, e, "serviceName",        t.getServiceName());
                addChild(doc, e, "scheduleType",       t.getScheduleType().name());
                addChild(doc, e, "scheduledAt",        t.getScheduledAt() != null ? t.getScheduledAt().format(DT_FMT) : "");
                addChild(doc, e, "intervalMinutes",    String.valueOf(t.getIntervalMinutes()));
                addChild(doc, e, "cronExpression",     t.getCronExpression() != null ? t.getCronExpression() : "");
                addChild(doc, e, "lastRunAt",          t.getLastRunAt() != null ? t.getLastRunAt().format(DT_FMT) : "");
                addChild(doc, e, "lastRunResult",      t.getLastRunResult() != null ? t.getLastRunResult() : "");
                addChild(doc, e, "createdAt",          t.getCreatedAt() != null ? t.getCreatedAt().format(DT_FMT) : "");
                root.appendChild(e);
            }
            writeXml(doc, taskFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ─── XML helpers ─────────────────────────────────────────────────────────

    private Credential elementToCredential(Element e) {
        Credential c = new Credential();
        c.setId(attr(e, "id"));
        c.setName(child(e, "name"));
        c.setHost(child(e, "host"));
        c.setUsername(child(e, "username"));
        c.setPassword(child(e, "password"));
        c.setOsType(child(e, "osType"));
        return c;
    }

    private Element credentialToElement(Document doc, Credential c) {
        Element e = doc.createElement("credential");
        e.setAttribute("id", c.getId());
        addChild(doc, e, "name",     c.getName());
        addChild(doc, e, "host",     c.getHost());
        addChild(doc, e, "username", c.getUsername());
        addChild(doc, e, "password", c.getPassword());    // plain text
        addChild(doc, e, "osType",   c.getOsType());
        return e;
    }

    private Document parseXml(File f) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(f);
    }

    private Document newDoc(String rootTag) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElement(rootTag));
        return doc;
    }

    private void writeXml(Document doc, File f) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.transform(new DOMSource(doc), new StreamResult(f));
    }

    private String attr(Element e, String name)  { return e.getAttribute(name); }

    private String child(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent();
    }

    private void addChild(Document doc, Element parent, String tag, String value) {
        Element e = doc.createElement(tag);
        e.setTextContent(value != null ? value : "");
        parent.appendChild(e);
    }

    public File getDataDir() { return dataDir; }
}
