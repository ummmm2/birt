/*******************************************************************************
 * Copyright (c) 2004,2008 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.api.impl;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.core.archive.IDocArchiveReader;
import org.eclipse.birt.report.engine.api.DataExtractionFormatInfo;
import org.eclipse.birt.report.engine.api.EmitterInfo;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.EngineConstants;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IDataExtractionTask;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IRenderTask;
import org.eclipse.birt.report.engine.api.IReportDocument;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.IRunTask;
import org.eclipse.birt.report.engine.api.IStatusHandler;
import org.eclipse.birt.report.engine.executor.ScriptUtil;
import org.eclipse.birt.report.engine.extension.engine.IReportEngineExtension;
import org.eclipse.birt.report.engine.extension.engine.IReportEngineExtensionFactory;
import org.eclipse.birt.report.model.api.IResourceLocator;
import org.eclipse.birt.report.model.api.ReportDesignHandle;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

/**
 * A report engine provides an entry point for reporting functionalities. It is
 * where the report generation and rendering process are globally customized. It
 * is also the place where engine statistics are collected. Through report
 * engine, reports can be generated and rendered to different output formats.
 * Queries can also be executed for preview purpose without involving a full
 * report generation.
 * <p>
 * Engine supports running different types of tasks. Example tasks include
 * running a report design to generate a report instance file, rendering a
 * report instance to output format, running a report directly to output,
 * running a dataset for preview, seaching a report, etc.
 */

public class ReportEngine implements IReportEngine
{

	public static final String PROPERTYSEPARATOR = File.pathSeparator;
	
	public static final boolean USE_DYNAMIC_SCOPE = true;

	static protected Logger logger = Logger.getLogger( ReportEngine.class
			.getName( ) );

	/**
	 * engine configuration object
	 */
	protected EngineConfig config;

	/**
	 * A helper object to carry out most ReportEngine jobs
	 */
	protected ReportEngineHelper helper;

	/**
	 * root script scope. contains objects shared by the whole engine.
	 */
	protected ScriptableObject rootScope;

	protected ClassLoader applicationClassLoader;
	
	private static String[] classPathes = new String[]{
		EngineConstants.WEBAPP_CLASSPATH_KEY,
		EngineConstants.PROJECT_CLASSPATH_KEY,
		EngineConstants.WORKSPACE_CLASSPATH_KEY};

	private EngineExtensionManager extensionManager = new EngineExtensionManager();
	/**
	 * Constructor. If config is null, engine derives BIRT_HOME from the
	 * location of the engine jar file, and derives data driver directory as
	 * $BIRT_HOME/drivers. For a simple report with no images and links, engine
	 * will run without complaining. If the report has image/chart defined, the
	 * engine has to be configured with relevant image and chart handlers.
	 * 
	 * @param config
	 *            an engine configuration object used to configure the engine
	 */
	public ReportEngine( EngineConfig config )
	{
		this.config = config;
		mergeConfigToAppContext( );

		intializeLogger( );

		logger.log( Level.FINE, "ReportEngine created. EngineConfig: {0} ",
				config );
		this.helper = new ReportEngineHelper( this );

		setupScriptScope( );
	}

	private void mergeConfigToAppContext( )
	{
		if ( config == null )
		{
			return;
		}
		mergeProperty( EngineConstants.APPCONTEXT_CLASSLOADER_KEY );
		mergeProperty( EngineConstants.WEBAPP_CLASSPATH_KEY );
		mergeProperty( EngineConstants.PROJECT_CLASSPATH_KEY );
		mergeProperty( EngineConstants.WORKSPACE_CLASSPATH_KEY );
	}

	private void mergeProperty( String property )
	{
		Map appContext = config.getAppContext( );
		// The configuration in appContext has higher priority.
		if ( appContext.containsKey( property ) )
		{
			return;
		}
		Object value = config.getProperty( property );
		if ( value != null )
		{
			appContext.put( property, value );
		}
	}

	/**
	 * set up engine logging
	 */
	private void intializeLogger( )
	{
		Logger logger = null;
		String dest = null;
		String file = null;
		Level level = Level.WARNING;
		if ( config != null )
		{
			logger = config.getLogger( );
			dest = config.getLogDirectory( );
			file = config.getLogFile( );
			level = config.getLogLevel( );
			if ( level == null )
			{
				level = Level.WARNING;
			}
		}
		EngineLogger.startEngineLogging( logger, dest, file, level );
	}
	
	static class MyFactory extends ContextFactory
	{

		protected boolean hasFeature( Context cx, int featureIndex )
		{
			if ( featureIndex == Context.FEATURE_DYNAMIC_SCOPE )
			{
				return USE_DYNAMIC_SCOPE;
			}
			return super.hasFeature( cx, featureIndex );
		}
	}

	static
	{
		ContextFactory.initGlobal( new MyFactory( ) );
	}

	/**
	 * register globally available script functions
	 */
	private void setupScriptScope( )
	{
		if ( config != null )
		{
			Context cx = Context.enter( );
			try
			{
				cx
						.setSecurityController( ScriptUtil
								.createSecurityController( ) );
			}
			catch ( Throwable throwable )
			{
			}
			try
			{
				rootScope = cx.initStandardObjects( );
				cx
				.evaluateString(
						rootScope,
						"function registerGlobal( name, value) { _jsContext.registerGlobalBean(name, value); }",
						"<inline>", 0, null );
				cx
				.evaluateString(
						rootScope,
						"function unregisterGlobal(name) { _jsContext.unregisterGlobalBean(name); }",
						"<inline>", 0, null );
				registerBeans( rootScope, config.getConfigMap( ) );
				registerBeans( rootScope, config.getScriptObjects( ) );
				IStatusHandler handler = config.getStatusHandler( );
				if ( handler != null )
				{
					handler.initialize( );
					rootScope.put( "_statusHandle", rootScope, handler );
					cx
							.evaluateString(
									rootScope,
									"function writeStatus(msg) { _statusHandle.showStatus(msg); }",
									"<inline>", 0, null );
				}
			}
			catch ( Exception ex )
			{
				rootScope = null;
				logger.log( Level.INFO,
						"Error occurs while initialze script scope", ex );
			}
			finally
			{
				Context.exit( );
			}
		}
	}

	/**
	 * get the root scope used by the engine
	 * 
	 * @return
	 */
	public Object getRootScope( )
	{
		return rootScope;
	}

	/**
	 * register the map entry into script object.
	 * 
	 * @param scope
	 *            script scope to be added.
	 * @param map
	 *            map
	 */
	private void registerBeans( ScriptableObject scope, Map map )
	{
		Iterator iter = map.entrySet( ).iterator( );
		while ( iter.hasNext( ) )
		{
			Map.Entry entry = (Map.Entry) iter.next( );
			if ( entry.getKey( ) != null )
			{
				scope
						.put( entry.getKey( ).toString( ), scope, entry
								.getValue( ) );
			}
		}
	}

	/**
	 * Change the log level to newLevel
	 * 
	 * @param newLevel -
	 *            new log level
	 */
	public void changeLogLevel( Level newLevel )
	{
		EngineLogger.changeLogLevel( newLevel );
	}

	/**
	 * returns the engine configuration object
	 * 
	 * @return the engine configuration object
	 */
	public EngineConfig getConfig( )
	{
		return config;
	}

	/**
	 * opens a report design file and creates a report design runnable. From the
	 * ReportRunnable object, embedded images and parameter definitions can be
	 * retrieved. Constructing an engine task requires a report design runnable
	 * object.
	 * 
	 * @param designName
	 *            the full path of the report design file
	 * @return a report design runnable object
	 * @throws EngineException
	 *             throwed when the input file does not exist, or the file is
	 *             invalid
	 */
	public IReportRunnable openReportDesign( String designName )
			throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: designName={0} ", designName );
		IResourceLocator locator = config.getResourceLocator( );
		return helper.openReportDesign( designName, locator);
	}
	
	/**
	 * opens a report designHandle and creates a report design runnable. From the
	 * ReportRunnable object, embedded images and parameter definitions can be
	 * retrieved. Constructing an engine task requires a report design runnable
	 * object.
	 * 
	 * @param designHandle
	 * @return a report design runnable object
	 * @throws EngineException
	 */
	public IReportRunnable openReportDesign( ReportDesignHandle designHandle )
			throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: designHandle={0} ", designHandle );
		return helper.openReportDesign( designHandle );
	}

	/**
	 * opens a report design stream and creates a report design runnable. From
	 * the ReportRunnable object, embedded images and parameter definitions can
	 * be retrieved. Constructing an engine task requires a report design
	 * runnableobject.
	 * 
	 * @param designStream
	 *            the report design input stream
	 * @return a report design runnable object
	 * @throws EngineException
	 *             throwed when the input stream is null, or the stream does not
	 *             yield a valid report design
	 */
	public IReportRunnable openReportDesign( InputStream designStream )
			throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: designStream={0} ", designStream );
		return helper.openReportDesign( designStream );
	}

	public IReportRunnable openReportDesign( String name,
			InputStream designStream ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: name={0}, designStream={1} ", 
				new Object[] {name, designStream} );
		return helper.openReportDesign( name, designStream );
	}

	/**
	 * creates an engine task for running and rendering report directly to
	 * output format
	 * 
	 * @param reportRunnable
	 *            the runnable report design object
	 * @return a run and render report task
	 */
	public IRunAndRenderTask createRunAndRenderTask(
			IReportRunnable reportRunnable )
	{
		logger.log( Level.FINE,
				"ReportEngine.createRunAndRenderTask: reportRunnable={0} ", reportRunnable );
		return helper.createRunAndRenderTask( reportRunnable );
	}

	/**
	 * creates an engine task for obtaining report parameter definitions
	 * 
	 * @param reportRunnable
	 *            the runnable report design object
	 * @return a run and render report task
	 */
	public IGetParameterDefinitionTask createGetParameterDefinitionTask(
			IReportRunnable reportRunnable )
	{
		logger.log( Level.FINE,
				"ReportEngine.createGetParameterDefinitionTask: reportRunnable={0} ", reportRunnable );
		return helper.createGetParameterDefinitionTask( reportRunnable );
	}

	/**
	 * returns all supported output formats through BIRT engine emitter
	 * extensions
	 * 
	 * @return all supported output formats through BIRT engine emitter
	 *         extensions
	 */
	public String[] getSupportedFormats( )
	{
		return helper.getSupportedFormats( );
	}
	
	/**
	 * Return all the emitter information which BIRT Engine can load.
	 * 
	 * @return the emitter information
	 */
	public EmitterInfo[] getEmitterInfo( )
	{
		return helper.getEmitterInfo( );
	}

	/**
	 * the MIME type for the specific formatted supported by the extension.
	 * 
	 * @param format
	 *            the output format
	 * @param extensionID
	 *            the extension ID, which could be null if only one plugin
	 *            supports the output format
	 * @return the MIME type for the specific formatted supported by the
	 *         extension.
	 */
	public String getMIMEType( String format )
	{
		return helper.getMIMEType( format );
	}

	/**
	 * shuts down the report engine
	 */
	public void destroy( )
	{
		logger.fine( "ReportEngine.destroy" );
		rootScope = null;
		helper = null;
		if ( config != null )
		{
			IStatusHandler handler = config.getStatusHandler( );
			if ( handler != null )
			{
				handler.finish( );
			}
		}
		if ( extensionManager != null )
		{
			extensionManager.close( );
			extensionManager = null;
		}
		EngineLogger.stopEngineLogging( );
	}

	/**
	 * creates a task to run a report to generate a report document
	 * 
	 * @param reportRunnable
	 *            the runnable report design object
	 * @return a task that runs the report
	 */
	public IRunTask createRunTask( IReportRunnable reportRunnable )
	{
		logger.log( Level.FINE,
				"ReportEngine.createRunTask: reportRunnable={0} ", reportRunnable );
		return helper.createRunTask( reportRunnable );
	}

	/**
	 * creates a task that renders the report to a specific output format.
	 * 
	 * @param reportDocument
	 *            a handle to an IReportDocument object
	 * @return a task that renders a report to an output format
	 */
	public IRenderTask createRenderTask( IReportDocument reportDocument )
	{
		logger.log( Level.FINE,
				"ReportEngine.createRenderTask: reportDocument={0} ", reportDocument );
		return helper.createRenderTask( reportDocument );
	}
	
	/**
	 * creates a task that renders the report to a specific output format.
	 * 
	 * @param reportDocument
	 *            a handle to an IReportDocument object
	 * @param reportRunnable
	 *            the runnable report design object
	 * @return a task that renders a report to an output format
	 */
	public IRenderTask createRenderTask( IReportDocument reportDocument,
			IReportRunnable reportRunnable )
	{
		logger
				.log(
						Level.FINE,
						"ReportEngine.createRenderTask: reportDocument={0}, runnable={1}",
						new Object[]{reportDocument, reportRunnable} );
		return helper.createRenderTask( reportDocument, reportRunnable );
	}

	/**
	 * opens a report document and returns an IReportDocument object, from which
	 * further information can be retrieved.
	 * 
	 * @param fileName
	 *            the report document name. report document is an archive in
	 *            BIRT.
	 * @return A handle to the report document
	 * @throws EngineException
	 *             throwed when the report document archive does not exist, or
	 *             the file is not a valud report document
	 */
	public IReportDocument openReportDocument( String fileName )
			throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDocument: fileName={0} ", fileName );
		return helper.openReportDocument( fileName );
	}

	/**
	 * creates a task that allows data extraction from a report document
	 * 
	 * @param reportDocument
	 *            a handle to an IReportDocument object
	 * @return a task that renders a report to an output format
	 */
	public IDataExtractionTask createDataExtractionTask(
			IReportDocument reportDocument )
	{
		logger.log( Level.FINE,
				"ReportEngine.createDataExtractionTask: reportDocument={0} ", reportDocument );
		return helper.createDataExtractionTask( reportDocument );
	}

	/**
	 * shut down the engine, release all the resources.
	 * 
	 * @deprecated
	 */
	public void shutdown( )
	{
		destroy();
	}

	public IReportDocument openReportDocument( String systemId,
			String fileName ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDocument: systemID={0}, file={1} ", 
				new Object[]{systemId, fileName}  );
		return openReportDocument( systemId, fileName, (IResourceLocator) null );
	}

	public IReportRunnable openReportDesign( String designName, IResourceLocator locator ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: design={0}, locator={1} ", 
				new Object[]{designName, locator}  );
		return helper.openReportDesign( designName, locator);
	}

	public IReportRunnable openReportDesign( String name, InputStream designStream, IResourceLocator locator ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDesign: name={0}, designStream={1}, locator={2} ", 
				new Object[]{name, designStream, locator}  );
		return helper.openReportDesign(name, designStream, locator);
	}
	
	public IReportRunnable openReportDesign( String name,
			InputStream designStream, Map options ) throws EngineException
	{
		logger
				.log(
						Level.FINE,
						"ReportEngine.openReportDesign: name={0}, designStream={1}, options={3} ",
						new Object[]{name, designStream, options} );
		return helper.openReportDesign( name, designStream, options );
	}
	

	public IReportDocument openReportDocument( String fileName, IResourceLocator locator ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDocument: file={0}, locator={1} ", 
				new Object[]{fileName, locator}  );
		return openReportDocument( fileName, fileName, locator );
	}

	public IReportDocument openReportDocument( String systemId,
			String fileName, IResourceLocator locator ) throws EngineException
	{
		logger.log( Level.FINE,
				"ReportEngine.openReportDocument: systemId={0}, file={1}, locator={2} ", 
				new Object[]{systemId, fileName, locator}  );
		return helper.openReportDocument( systemId, fileName, locator );
	}

	public IReportDocument openReportDocument( String systemId,
			String fileName, Map options ) throws EngineException
	{
		logger
				.log(
						Level.FINE,
						"ReportEngine.openReportDocument: systemId={0}, file={1}, options={2} ",
						new Object[]{systemId, fileName, options} );
		return helper.openReportDocument( systemId, fileName, options );
	}
	
	public IReportDocument openReportDocument( String systemId,
			IDocArchiveReader reader, Map options ) throws EngineException
	{
		logger
				.log(
						Level.FINE,
						"ReportEngine.openReportDocument: systemId={0}, reader={1}, options={2} ",
						new Object[]{systemId, reader, options} );
		return helper.openReportDocument( systemId, reader, options );
	}

	public Logger getLogger( )
	{
		return logger;
	}

	public void setLogger( Logger logger )
	{
		if ( logger != null )
		{
			EngineLogger.startEngineLogging( logger, null, null, null );
		}
	}

	public ClassLoader getClassLoader( )
	{
		if ( applicationClassLoader == null )
		{
			applicationClassLoader = createClassLoaderFromEngine( this );
		}
		return applicationClassLoader;
	}

	private static ClassLoader createClassLoaderFromEngine( IReportEngine engine )
	{
		ClassLoader root = getAppClassLoader( engine );
		if ( root == null )
		{
			root = IReportEngine.class.getClassLoader( );
		}
		return createClassLoaderFromProperty( engine, root );
	}

	private static ClassLoader createClassLoaderFromProperty(
			IReportEngine engine, ClassLoader parent )
	{
		EngineConfig config = engine.getConfig( );
		if ( config == null )
		{
			return parent;
		}
		Map appContext = config.getAppContext( );
		ArrayList urls = new ArrayList( );
		if ( appContext != null )
		{
			for ( int i = 0; i < classPathes.length; i++ )
			{
				String classPath = null;
				Object propValue = appContext.get( classPathes[i] );
				if ( propValue instanceof String )
				{
					classPath = (String) propValue;
				}

				if ( classPath == null )
				{
					classPath = System.getProperty( classPathes[i] );
				}

				if ( classPath != null && classPath.length( ) != 0 )
				{
					String[] jars = classPath.split( PROPERTYSEPARATOR, -1 );
					if ( jars != null && jars.length != 0 )
					{
						for ( int j = 0; j < jars.length; j++ )
						{
							File file = new File( jars[j] );
							try
							{
								urls.add( file.toURL( ) );
							}
							catch ( MalformedURLException e )
							{
								logger.log( Level.WARNING, e.getMessage( ), e );
							}
						}
					}
				}
			}
		}
		if ( urls.size( ) != 0 )
		{
			return new URLClassLoader( (URL[]) urls.toArray( new URL[urls
					.size( )] ), parent );
		}
		return parent;
	}

	private static ClassLoader getAppClassLoader( IReportEngine engine )
	{
		EngineConfig config = engine.getConfig( );
		if ( config == null )
		{
			return null;
		}
		Map appContext = config.getAppContext( );
		if ( appContext != null )
		{
			Object appLoader = appContext
					.get( EngineConstants.APPCONTEXT_CLASSLOADER_KEY );
			if ( appLoader instanceof ClassLoader )
			{
				return (ClassLoader) appLoader;
			}
		}
		return null;
	}

	public DataExtractionFormatInfo[] getDataExtractionFormatInfo( )
	{
		return helper.getDataExtractionFormatInfo( );
	}

	public IReportEngineExtension getEngineExtension( String name )
	{
		if ( extensionManager != null )
		{
			return extensionManager.getExtension( name );
		}
		return null;
	}
	
	private class EngineExtensionManager
	{

		HashMap<String, IReportEngineExtension> exts = new HashMap<String, IReportEngineExtension>( );

		EngineExtensionManager( )
		{
			IExtensionRegistry registry = Platform.getExtensionRegistry( );
			IExtensionPoint extensionPoint = registry
					.getExtensionPoint( "org.eclipse.birt.core.FactoryService" );
			IExtension[] extensions = extensionPoint.getExtensions( );
			for ( IExtension extension : extensions )
			{
				IConfigurationElement[] elements = extension
						.getConfigurationElements( );
				for ( IConfigurationElement element : elements )
				{
					String type = element.getAttribute( "type" );
					if ( "org.eclipse.birt.report.engine.extension"
							.equals( type ) )
					{
						try
						{
							Object factoryObject = element
									.createExecutableExtension( "class" );
							if ( factoryObject instanceof IReportEngineExtensionFactory )
							{
								IReportEngineExtensionFactory factory = (IReportEngineExtensionFactory) factoryObject;
								IReportEngineExtension engineExtension = factory
										.createExtension( ReportEngine.this );
								exts.put( engineExtension.getExtensionName( ),
										engineExtension );
							}
						}
						catch ( CoreException ex )
						{
							logger.log( Level.WARNING,
									"can't load the engine extension factory",
									ex );
						}
					}
				}
			}
		}

		synchronized IReportEngineExtension getExtension( String name )
		{
			if ( exts.containsKey( name ) )
			{
				return exts.get( name );
			}
			return null;
		}

		synchronized void close( )
		{
			for ( IReportEngineExtension ext : exts.values( ) )
			{
				if ( ext != null )
				{
					ext.close( );
				}
			}
			exts.clear( );
		}
	}
}