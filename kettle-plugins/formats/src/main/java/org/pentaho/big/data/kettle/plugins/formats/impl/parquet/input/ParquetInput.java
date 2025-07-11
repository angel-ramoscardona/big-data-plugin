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


package org.pentaho.big.data.kettle.plugins.formats.impl.parquet.input;

import org.apache.commons.vfs2.FileObject;
import org.pentaho.big.data.kettle.plugins.formats.parquet.input.ParquetInputField;
import org.pentaho.big.data.kettle.plugins.formats.parquet.input.ParquetInputMetaBase;
import org.pentaho.di.core.bowl.Bowl;
import org.pentaho.di.core.RowMetaAndData;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.util.StringUtil;
import org.pentaho.di.core.vfs.AliasedFileObject;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.trans.steps.file.BaseFileInputStep;
import org.pentaho.di.trans.steps.file.IBaseFileInputReader;
import org.pentaho.hadoop.shim.api.cluster.NamedCluster;
import org.pentaho.hadoop.shim.api.cluster.NamedClusterServiceLocator;
import org.pentaho.hadoop.shim.api.format.FormatService;
import org.pentaho.hadoop.shim.api.format.IParquetInputField;
import org.pentaho.hadoop.shim.api.format.IPentahoInputFormat.IPentahoInputSplit;
import org.pentaho.hadoop.shim.api.format.IPentahoParquetInputFormat;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParquetInput extends BaseFileInputStep<ParquetInputMeta, ParquetInputData> {
  public static final long SPLIT_SIZE = 128 * 1024 * 1024L;

  public ParquetInput( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
                       Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
  }

  public static List<? extends IParquetInputField> retrieveSchema(
    Bowl bowl, NamedClusterServiceLocator namedClusterServiceLocator, NamedCluster namedCluster, String path )
    throws Exception {
    FormatService formatService = namedClusterServiceLocator.getService( namedCluster, FormatService.class );
    IPentahoParquetInputFormat in = formatService.createInputFormat( IPentahoParquetInputFormat.class, namedCluster );
    FileObject inputFileObject = KettleVFS.getInstance( bowl ).getFileObject( path );
    if ( AliasedFileObject.isAliasedFile( inputFileObject ) ) {
      path = ( (AliasedFileObject) inputFileObject ).getOriginalURIString();
    }
    return in.readSchema( path );
  }

  public static List<IParquetInputField> createSchemaFromMeta( ParquetInputMetaBase meta ) {
    List<IParquetInputField> fields = new ArrayList<>();
    for ( ParquetInputField f : meta.getInputFields() ) {
      fields.add( f );
    }
    return fields;
  }

  @Override public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {
    meta = (ParquetInputMeta) smi;
    data = (ParquetInputData) sdi;

    try {
      if ( data.splits == null ) {
        initSplits();
      }

      if ( data.currentSplit >= data.splits.size() ) {
        setOutputDone();
        return false;
      }

      if ( data.reader == null ) {
        openReader( data );
      }

      if ( data.rowIterator.hasNext() ) {
        RowMetaAndData row = data.rowIterator.next();
        putRow( row.getRowMeta(), row.getData() );
        return true;
      } else {
        data.reader.close();
        data.reader = null;
        logDebug( "Close split {0}", data.currentSplit );
        data.currentSplit++;
        return true;
      }
    } catch ( NoSuchFileException ex ) {
      throw new KettleException( "No input file" );
    } catch ( KettleException ex ) {
      throw ex;
    } catch ( Exception ex ) {
      throw new KettleException( ex );
    }
  }

  void initSplits() throws Exception {
    FormatService
      formatService =
      meta.getNamedClusterResolver().getNamedClusterServiceLocator()
        .getService( getNamedCluster(), FormatService.class );
    if ( meta.inputFiles == null || meta.inputFiles.fileName == null || meta.inputFiles.fileName.length == 0 ) {
      throw new KettleException( "No input files defined" );
    }
    String[] resolvedInputFileNames = new String[ meta.inputFiles.fileName.length ];
    int i = 0;
    for ( String file : meta.inputFiles.fileName ) {
      resolvedInputFileNames[ i ] = StringUtil.toUri( environmentSubstitute( file ) ).toString();
      FileObject inputFileObject = KettleVFS.getInstance( getTransMeta().getBowl() )
        .getFileObject( resolvedInputFileNames[ i ], getTransMeta() );
      if ( AliasedFileObject.isAliasedFile( inputFileObject ) ) {
        resolvedInputFileNames[ i ] = ( (AliasedFileObject) inputFileObject ).getOriginalURIString();
      }
      i++;
    }
    data.input = formatService.createInputFormat( IPentahoParquetInputFormat.class, getNamedCluster() );

    // Pentaho 8.0 transformations will have the formatType set to 0. Get the fields from the schema and set the
    // formatType to the formatType retrieved from the schema.
    List<? extends IParquetInputField>
      actualFileFields =
      ParquetInput.retrieveSchema( getTransMeta().getBowl(),
        meta.getNamedClusterResolver().getNamedClusterServiceLocator(), getNamedCluster(), resolvedInputFileNames[ 0 ] );

    if ( meta.isIgnoreEmptyFolder() && ( actualFileFields.isEmpty() ) ) {
      data.splits = new ArrayList<>();
      logBasic( "No Parquet input files found." );
    } else {
      Map<String, IParquetInputField>
        fieldNamesToTypes =
        actualFileFields.stream()
          .collect( Collectors.toMap( IParquetInputField::getFormatFieldName, Function.identity() ) );
      for ( ParquetInputField f : meta.getInputFields() ) {
        if ( fieldNamesToTypes.containsKey( f.getFormatFieldName() ) ) {
          if ( f.getFormatType() == 0 ) {
            f.setFormatType( fieldNamesToTypes.get( f.getFormatFieldName() ).getFormatType() );
          }
          f.setPrecision( fieldNamesToTypes.get( f.getFormatFieldName() ).getPrecision() );
          f.setScale( fieldNamesToTypes.get( f.getFormatFieldName() ).getScale() );
        }
      }

      data.input.setSchema( createSchemaFromMeta( meta ) );
      if ( resolvedInputFileNames != null && resolvedInputFileNames.length == 1 ) {
        data.input.setInputFile( resolvedInputFileNames[ 0 ] );
      } else if ( resolvedInputFileNames != null && resolvedInputFileNames.length > 1 ) {
        data.input.setInputFiles( resolvedInputFileNames );
      }
      data.input.setSplitSize( SPLIT_SIZE );

      data.splits = data.input.getSplits();
      logDebug( "Input split count: {0}", data.splits.size() );
    }
    data.currentSplit = 0;
  }

  private NamedCluster getNamedCluster() {
    return meta.getNamedClusterResolver().resolveNamedCluster( environmentSubstitute( meta.getFilename() ) );
  }

  void openReader( ParquetInputData data ) throws Exception {
    logDebug( "Open split {0}", data.currentSplit );
    IPentahoInputSplit sp = data.splits.get( data.currentSplit );
    data.reader = data.input.createRecordReader( sp );
    data.rowIterator = data.reader.iterator();
  }

  @Override protected boolean init() {
    return true;
  }

  @Override protected IBaseFileInputReader createReader( ParquetInputMeta meta, ParquetInputData data, FileObject file )
    throws Exception {
    return null;
  }
}
