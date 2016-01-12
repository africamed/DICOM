/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4che3.conf.json;

import org.dcm4che3.conf.json.audit.JsonAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.json.imageio.JsonImageReaderConfiguration;
import org.dcm4che3.conf.json.imageio.JsonImageWriterConfiguration;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.junit.Test;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2015
 */
public class JsonConfigurationTest {

    @Test
    public void testWriteTo() throws Exception {
        StringWriter writer = new StringWriter();
        try ( JsonGenerator gen = Json.createGenerator(writer)) {
            JsonConfiguration config = new JsonConfiguration();
            config.addJsonConfigurationExtension(new JsonAuditRecordRepositoryConfiguration());
            config.addJsonConfigurationExtension(new JsonImageReaderConfiguration());
            config.addJsonConfigurationExtension(new JsonImageWriterConfiguration());
            config.writeTo(createDevice("Test-Device-1", "TEST-AET1"), gen);
        }
        Path path = Paths.get("target/device.json");
        try (BufferedWriter w = Files.newBufferedWriter(path, Charset.forName("UTF-8"))) {
            w.write(writer.toString());
        }
//        Path path = Paths.get("src/test/data/device.json");
//        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
//            assertEquals(reader.readLine(), writer.toString());
//        }
    }

    @Test
    public void testWriteARRTo() throws Exception {
        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = Json.createGenerator(writer)) {
            JsonConfiguration config = new JsonConfiguration();
            config.addJsonConfigurationExtension(new JsonAuditRecordRepositoryConfiguration());
            config.writeTo(createARRDevice("TestAuditRecordRepository"), gen);
        }
        Path path = Paths.get("src/test/data/arrdevice.json");
        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            assertEquals(reader.readLine(), writer.toString());
        }
    }

    @Test
    public void testLoadDevice() throws Exception {
        Device device = null;
        Path path = Paths.get("src/test/data/device.json");
        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            device = new JsonConfiguration().loadDeviceFrom(Json.createParser(reader));
        }
        assertEquals("Test-Device-1", device.getDeviceName());
        List<Connection> conns = device.listConnections();
        assertEquals(1, conns.size());
        Connection conn = conns.get(0);
        assertEquals("host.dcm4che.org", conn.getHostname());
        assertEquals(11112, conn.getPort());
        Collection<ApplicationEntity> aes = device.getApplicationEntities();
        assertEquals(1, aes.size());
        ApplicationEntity ae = aes.iterator().next();
        assertEquals("TEST-AET1", ae.getAETitle());
        assertTrue(ae.isAssociationAcceptor());
        assertFalse(ae.isAssociationInitiator());
        List<Connection> aeconns = ae.getConnections();
        assertEquals(1, aeconns.size());
        assertSame(conn, aeconns.get(0));
        assertEquals(3, ae.getTransferCapabilities().size());
        TransferCapability echoSCP = ae.getTransferCapabilityFor(UID.VerificationSOPClass, TransferCapability.Role.SCP);
        assertNotNull(echoSCP);
        assertArrayEquals(new String[]{ UID.ImplicitVRLittleEndian }, echoSCP.getTransferSyntaxes());
        assertNull(echoSCP.getCommonName());
        assertNull(echoSCP.getQueryOptions());
        assertNull(echoSCP.getStorageOptions());
        TransferCapability ctSCP = ae.getTransferCapabilityFor(UID.CTImageStorage, TransferCapability.Role.SCP);
        assertNotNull(ctSCP);
        StorageOptions storageOptions = ctSCP.getStorageOptions();
        assertNotNull(storageOptions);
        assertEquals(StorageOptions.LevelOfSupport.LEVEL_2, storageOptions.getLevelOfSupport());
        assertEquals(StorageOptions.DigitalSignatureSupport.LEVEL_1, storageOptions.getDigitalSignatureSupport());
        assertEquals(StorageOptions.ElementCoercion.YES, storageOptions.getElementCoercion());
        TransferCapability findSCP = ae.getTransferCapabilityFor(
                UID.StudyRootQueryRetrieveInformationModelFIND, TransferCapability.Role.SCP);
        assertNotNull(findSCP);
        assertEquals(EnumSet.of(QueryOption.RELATIONAL), findSCP.getQueryOptions());
    }

    private static Device createDevice(String name, String aet) throws Exception {
        Device device = new Device(name);
        Connection conn = createConn("host.dcm4che.org", 11112);
        device.addConnection(conn);
        ApplicationEntity ae = createAE(aet, conn);
        device.addApplicationEntity(ae);
        device.addDeviceExtension(new ImageReaderExtension(ImageReaderFactory.getDefault()));
        device.addDeviceExtension(new ImageWriterExtension(ImageWriterFactory.getDefault()));
        return device ;
    }

    private static Connection createConn(String hostname, int port) {
        Connection conn = new Connection();
        conn.setHostname(hostname);
        conn.setPort(port);
        return conn;
    }

    private static TransferCapability echoSCP() {
        return new TransferCapability(null, UID.VerificationSOPClass, TransferCapability.Role.SCP,
                UID.ImplicitVRLittleEndian);
    }

    private static final TransferCapability ctSCP() {
        TransferCapability tc = new TransferCapability(null, UID.CTImageStorage, TransferCapability.Role.SCP,
                UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian);
        tc.setStorageOptions(STORAGE_OPTIONS);
        return tc;
    }

    private static final TransferCapability findSCP() {
        TransferCapability tc = new TransferCapability(null,
                UID.StudyRootQueryRetrieveInformationModelFIND, TransferCapability.Role.SCP,
                UID.ImplicitVRLittleEndian);
        tc.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
        return tc;
    }

    private static final StorageOptions STORAGE_OPTIONS = new StorageOptions(
            StorageOptions.LevelOfSupport.LEVEL_2,
            StorageOptions.DigitalSignatureSupport.LEVEL_1,
            StorageOptions.ElementCoercion.YES);

    private static ApplicationEntity createAE(String aet, Connection conn) {
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setAssociationInitiator(false);
        ae.addConnection(conn);
        ae.addTransferCapability(echoSCP());
        ae.addTransferCapability(ctSCP());
        ae.addTransferCapability(findSCP());
        return ae;
    }

    private static Device createARRDevice(String name) {
        Device device = new Device(name);
        Connection udp = new Connection("audit-udp", "host.dcm4che.org", 514);
        udp.setProtocol(Connection.Protocol.SYSLOG_UDP);
        Connection tls = new Connection("audit-tls", "host.dcm4che.org", 6514);
        tls.setProtocol(Connection.Protocol.SYSLOG_TLS);
        tls.setTlsCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
        device.addConnection(udp);
        device.addConnection(tls);
        addAuditRecordRepository(device, udp, tls);
        return device ;
    }

    private static void addAuditRecordRepository(Device device, Connection udp, Connection tls) {
        AuditRecordRepository arr = new AuditRecordRepository();
        device.addDeviceExtension(arr);
        arr.addConnection(udp);
        arr.addConnection(tls);
    }
}