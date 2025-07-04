/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.big.data.kettle.plugins.oozie;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pentaho.hadoop.shim.api.cluster.NamedCluster;
import org.pentaho.hadoop.shim.api.cluster.NamedClusterService;
import org.pentaho.hadoop.shim.api.cluster.NamedClusterServiceLocator;
import org.pentaho.big.data.kettle.plugins.job.JobEntryMode;
import org.pentaho.big.data.kettle.plugins.job.PropertyEntry;
import org.pentaho.big.data.plugins.common.ui.HadoopClusterDelegateImpl;
import org.pentaho.di.core.bowl.DefaultBowl;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.job.JobMeta;
import org.pentaho.metastore.api.exceptions.MetaStoreException;
import org.pentaho.runtime.test.RuntimeTester;
import org.pentaho.runtime.test.action.RuntimeTestActionService;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.DefaultBindingFactory;
import org.pentaho.ui.xul.containers.XulDeck;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.impl.XulFragmentContainer;
import org.pentaho.ui.xul.util.AbstractModelList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

/**
 * User: RFellows Date: 6/4/12
 */
public class OozieJobExecutorControllerTest {

  @Mock HadoopClusterDelegateImpl delegate;
  @Mock NamedCluster cluster;
  @Mock NamedCluster cluster2;

  OozieJobExecutorConfig jobConfig = null;
  OozieJobExecutorJobEntryController controller = null;

  @BeforeClass
  public static void init() throws KettleException {
    KettleEnvironment.init();
  }

  @Before
  public void before() throws XulException, MetaStoreException {
    MockitoAnnotations.initMocks( this );

    jobConfig = new OozieJobExecutorConfig();
    jobConfig.setOozieWorkflow( "hdfs://localhost:9000/user/" + System.getProperty( "user.name" )
      + "/examples/apps/map-reduce" );

    NamedClusterService namedClusterService = mock( NamedClusterService.class );
    when( namedClusterService.list( any() ) ).thenReturn( Arrays.asList( cluster ) );
    OozieJobExecutorJobEntry jobEntry = new OozieJobExecutorJobEntry(
      namedClusterService,
      mock( RuntimeTestActionService.class ),
      mock( RuntimeTester.class ),
      mock( NamedClusterServiceLocator.class ) );
    jobEntry.setParentJobMeta( new JobMeta() );

    controller =
      new OozieJobExecutorJobEntryController( new JobMeta(), new XulFragmentContainer( null ),
        jobEntry, new DefaultBindingFactory(), delegate );
  }

  @Test
  public void testSetModeToggleLabel_JobEntryMode() throws Exception {
    assertEquals( controller.getModeToggleLabel(), null );

    controller.setModeToggleLabel( JobEntryMode.QUICK_SETUP );
    assertEquals( controller.getModeToggleLabel(), "Quick Setup" );

    controller.setModeToggleLabel( JobEntryMode.ADVANCED_LIST );
    assertEquals( controller.getModeToggleLabel(), "Advanced Options" );

  }

  @Test
  public void testGetNamedClusterOnChangedDataInClusterNamedService() throws Exception {
    NamedClusterService namedClusterService = mock( NamedClusterService.class );
    when( namedClusterService.list( any() ) ).thenReturn( Arrays.asList( cluster ) );
    OozieJobExecutorJobEntry jobEntry = new OozieJobExecutorJobEntry(
      namedClusterService,
      mock( RuntimeTestActionService.class ),
      mock( RuntimeTester.class ),
      mock( NamedClusterServiceLocator.class ) );
    OozieJobExecutorJobEntryController controller =
      new OozieJobExecutorJobEntryController( new JobMeta(), new XulFragmentContainer( null ),
        jobEntry, new DefaultBindingFactory(),
        delegate );

    assertEquals( controller.getNamedClusters().size(), 1 );
    assertEquals( controller.getNamedClusters().get( 0 ), cluster );

    when( namedClusterService.list( any() ) ).thenReturn( Arrays.asList( cluster, cluster2 ) );
    List<NamedCluster> namedClusters = controller.getNamedClusters();
    assertEquals( namedClusters.size(), 2 );
    assertEquals( namedClusters.get( 1 ), cluster2 );
  }

  @Test
  public void testReturnEmptyCollectionOnNamedClusterServiceThrowMetaStoreException() throws Exception {
    NamedClusterService namedClusterService = mock( NamedClusterService.class );
    OozieJobExecutorJobEntry jobEntry = new OozieJobExecutorJobEntry(
      namedClusterService,
      mock( RuntimeTestActionService.class ),
      mock( RuntimeTester.class ),
      mock( NamedClusterServiceLocator.class ) );
    OozieJobExecutorJobEntryController controller =
      new OozieJobExecutorJobEntryController( new JobMeta(), new XulFragmentContainer( null ),
        jobEntry, new DefaultBindingFactory(), delegate );
    when( jobEntry.getNamedClusterService().list( anyObject() ) ).thenThrow( new MetaStoreException() );
    List<NamedCluster> namedClusters = controller.getNamedClusters();
    assertEquals( namedClusters.size(), 0 );
  }

  @Test
  public void testConfigNamedClustersChangedOnPopulateNamedClusters() throws Exception {
    NamedClusterService namedClusterService = mock( NamedClusterService.class );
    when( namedClusterService.list( any() ) ).thenReturn( Arrays.asList( cluster ) );
    OozieJobExecutorJobEntry jobEntry = new OozieJobExecutorJobEntry(
      namedClusterService,
      mock( RuntimeTestActionService.class ),
      mock( RuntimeTester.class ),
      mock( NamedClusterServiceLocator.class ) );
    OozieJobExecutorJobEntryController controller =
      new OozieJobExecutorJobEntryController( new JobMeta(), new XulFragmentContainer( null ),
        jobEntry, new DefaultBindingFactory(),
        delegate );

    controller.populateNamedClusters();
    assertEquals( controller.getConfig().getNamedClusters().size(), 1 );

    when( namedClusterService.list( any() ) ).thenReturn( Arrays.asList( cluster, cluster2 ) );
    controller.populateNamedClusters();
    assertEquals( controller.getConfig().getNamedClusters().size(), 2 );

    when( namedClusterService.list( any() ) ).thenReturn( Collections.emptyList() );
    controller.populateNamedClusters();
    assertEquals( controller.getConfig().getNamedClusters().size(), 0 );
  }

  @Test( expected = RuntimeException.class )
  public void testSetModeToggleLabel_UnsupportedJobEntryMode() {
    controller.setModeToggleLabel( JobEntryMode.ADVANCED_COMMAND_LINE );
    fail( "JobEntryMode.ADVANCED_COMMAND_LINE is not supported, should have gotten a RuntimeException" );
  }

  @Test
  public void testSyncModel_quickSetupMode() throws Exception {
    assertEquals( 0, controller.getAdvancedArguments().size() );

    // set the props file, sync the model... should have equal amounts of elements
    OozieJobExecutorConfig config = getGoodConfig();
    controller.setConfig( config );
    controller.setJobEntryMode( JobEntryMode.QUICK_SETUP );
    assertEquals( 0, controller.getAdvancedArguments().size() );
    controller.syncModel();
    assertEquals( 0, controller.getAdvancedArguments().size() );
  }

  @Test
  public void testSyncModel_advancedMode() throws Exception {
    assertEquals( 0, controller.getAdvancedArguments().size() );

    // set the props file, sync the model... should have equal amounts of elements
    OozieJobExecutorConfig config = getGoodConfig();
    controller.setConfig( config );
    controller.setJobEntryMode( JobEntryMode.ADVANCED_LIST );
    Properties props = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), config, new Variables() );

    assertFalse( props.size() == controller.getAdvancedArguments().size() );
    controller.syncModel();
    assertEquals( props.size(), controller.getAdvancedArguments().size() );
  }

  @Test
  public void testSyncModel_advanced_addedProp() throws Exception {
    OozieJobExecutorConfig config = getGoodConfig();
    controller.setConfig( config );
    Properties props = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), config, new Variables() );
    controller.setJobEntryMode( JobEntryMode.ADVANCED_LIST );

    controller.syncModel();

    controller.addNewProperty();
    controller.syncModel();
    assertTrue( controller.isAdvancedArgumentsChanged() );
    assertEquals( props.size() + 1, controller.getAdvancedArguments().size() );
  }

  // ignoring for now, remove depends on the tree having selected items...
  @Ignore
  @Test
  public void testSyncModel_advanced_removedProp() throws Exception {
    OozieJobExecutorConfig config = getGoodConfig();

    controller.setConfig( config );
    Properties props = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), config, new Variables() );
    controller.syncModel();
    controller.setJobEntryMode( JobEntryMode.ADVANCED_LIST );

    controller.variablesTree.setSelectedRows( new int[] { 0 } );
    controller.removeProperty();
    controller.syncModel();
    assertTrue( controller.isAdvancedArgumentsChanged() );
    assertEquals( props.size() - 1, controller.getAdvancedArguments().size() );
  }

  @Test
  public void testSyncModel_advanced_editProp() throws Exception {
    OozieJobExecutorConfig config = getGoodConfig();
    controller.setConfig( config );
    Properties props = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), config, new Variables() );
    controller.setJobEntryMode( JobEntryMode.ADVANCED_LIST );

    controller.syncModel();

    String key = controller.getAdvancedArguments().get( 0 ).getKey();
    AbstractModelList<PropertyEntry> advanced = controller.getAdvancedArguments();
    advanced.get( 0 ).setValue( "new value" );
    controller.setAdvancedArguments( advanced );
    controller.syncModel();
    assertEquals( props.size(), controller.getAdvancedArguments().size() );
    Properties updatedProps = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(),
      controller.getConfig(), new Variables() );
    assertEquals( "new value", updatedProps.get( key ) );
  }

  @Test
  public void testToggleMode() throws Exception {
    // get into advanced mode
    TestOozieJobExecutorController ctr = new TestOozieJobExecutorController();
    ctr.getJobEntry().setParentJobMeta( new JobMeta() );

    OozieJobExecutorConfig config = getGoodConfig();
    ctr.setConfig( config );
    Properties props = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), config, new Variables() );
    ctr.syncModel();

    ctr.setJobEntryMode( JobEntryMode.ADVANCED_LIST );

    ctr.syncModel();

    String key = ctr.getAdvancedArguments().get( 0 ).getKey();
    AbstractModelList<PropertyEntry> advanced = ctr.getAdvancedArguments();
    advanced.get( 0 ).setValue( "new value" );
    ctr.setAdvancedArguments( advanced );
    ctr.syncModel();
    assertEquals( props.size(), ctr.getAdvancedArguments().size() );
    Properties updatedProps = OozieJobExecutorJobEntry.getProperties( DefaultBowl.getInstance(), ctr.getConfig(),
      new Variables() );
    assertEquals( "new value", updatedProps.get( key ) );
    assertEquals( props.size(), ctr.getConfig().getWorkflowProperties().size() );

    // make sure if set to QUICK_SETUP that we clear out any custom props from advanced mode
    ctr.toggleMode();
    assertEquals( 0, ctr.getConfig().getWorkflowProperties().size() );

  }

  @Test
  public void testShouldUseAdvancedProperties_basicMode() throws Exception {
    OozieJobExecutorConfig config = getGoodConfig();
    controller.setConfig( config );
    assertFalse( controller.shouldUseAdvancedProperties() );
  }

  @Test
  public void testAddProperty_exception() throws Exception {
    AbstractModelList<PropertyEntry> argumentsMock = mock( AbstractModelList.class );
    doThrow( Exception.class ).when( argumentsMock ).add( (PropertyEntry) any() );
    controller.advancedArguments = argumentsMock;

    XulTree treeMock = mock( XulTree.class );
    controller.variablesTree = treeMock;

    controller.addNewProperty();
    verify( treeMock ).setElements( argumentsMock );
  }

  private OozieJobExecutorConfig getGoodConfig() {
    OozieJobExecutorConfig config = new OozieJobExecutorConfig();
    config.setOozieUrl( "http://localhost:11000/oozie" ); // don't worry if it isn't running, we fake out our test
    // connection to it anyway
    config.setOozieWorkflowConfig( "src/test/resources/job.properties" );
    config.setJobEntryName( "name" );
    return config;
  }

  // stub classes
  class TestOozieJobExecutorController extends OozieJobExecutorJobEntryController {
    @SuppressWarnings( "unused" )
    private XulDeck modeDeck;

    private List<Object[]> shownErrors = new ArrayList<Object[]>();
    private boolean infoShown = false;

    TestOozieJobExecutorController() {
      this( null );
    }

    public TestOozieJobExecutorController( XulDeck modeDeck ) {
      super( new JobMeta(), new XulFragmentContainer( null ), new OozieJobExecutorJobEntry(
          mock( NamedClusterService.class ),
          mock( RuntimeTestActionService.class ),
          mock( RuntimeTester.class ),
          mock( NamedClusterServiceLocator.class ) ),
        new DefaultBindingFactory(), delegate );

      this.modeDeck = modeDeck;
      syncModel();
    }

    @Override
    protected void showErrorDialog( String title, String message ) {
      shownErrors.add( new Object[] { title, message, null } );
    }

    @Override
    protected void showErrorDialog( String title, String message, Throwable t ) {
      shownErrors.add( new Object[] { title, message, t } );
    }

    @Override
    protected void showInfoDialog( String title, String message ) {
      infoShown = true;
    }

    public List<Object[]> getShownErrors() {
      return shownErrors;
    }

    public boolean wasInfoShown() {
      return infoShown;
    }

    public void setJobEntry( OozieJobExecutorJobEntry je ) {
      jobEntry = je;
    }

    @Override
    protected boolean showConfirmationDialog( String title, String message ) {
      return true;
    }

    @Override
    public void toggleMode() {
      JobEntryMode mode =
        ( jobEntryMode == JobEntryMode.ADVANCED_LIST ? JobEntryMode.QUICK_SETUP : JobEntryMode.ADVANCED_LIST );
      this.setJobEntryMode( mode );
      this.syncModel();
    }
  }

  public class TestOozieJobExecutorJobEntry extends OozieJobExecutorJobEntry {
    @Override
    public List<String> getValidationWarnings( OozieJobExecutorConfig config ) {
      return new ArrayList<String>();
    }
  }

}
