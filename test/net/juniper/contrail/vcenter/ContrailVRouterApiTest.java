package net.juniper.contrail.vcenter;

import java.io.IOException;
import net.juniper.contrail.vcenter.ContrailVRouterApi;
import junit.framework.TestCase;
import org.junit.Test;
import org.apache.log4j.Logger;
import org.junit.Before;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.ApiSerializer;
import net.juniper.contrail.api.types.Project;

//@RunWith(MockitoJUnitRunner.class)
//@RunWith(JUnit4.class)
//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ContrailVRouterApiTest extends TestCase {
//public class ContrailVRouterApiTest  {

    private ContrailVRouterApi vrouterApi;
    private ApiConnector api;

    private static final Logger s_logger =
            Logger.getLogger(ContrailVRouterApiTest.class);

    @Before
    public void globalSetUp() throws IOException {

        vrouterApi = new ContrailVRouterApi(null, 0);

        api   = new ApiConnectorMock(null, 0);

        vrouterApi.setApiConnector(api);

     // Create default-domain,default-project
        Project vProject = new Project();
        vProject.setName("default-project");
        try {
            if (!api.create(vProject)) {
                s_logger.error("Unable to create project: " + vProject.getName());
                fail("default-project creation failed");
                return;
            }
        } catch (IOException e) {
            s_logger.error("Exception : " + e);
            e.printStackTrace();
            fail("default-project creation failed");
            return;
        }
    }

    @Test
    public void testAddDeletePort() {
        vrouterApi = new ContrailVRouterApi(null, 0);

        api   = new ApiConnectorMock(null, 0);

        vrouterApi.setApiConnector(api);

        vrouterApi.addPort("VIF_UUID", "VM_UUID", "INTF_NAME", "INTF_IP", "MAC_ADDRESS",
                "NW_UUID", (short)1000, (short)1001, "VM-NAME", "PROJ_UUID");

        vrouterApi.deletePort("VIF_UUID");
    }
}
