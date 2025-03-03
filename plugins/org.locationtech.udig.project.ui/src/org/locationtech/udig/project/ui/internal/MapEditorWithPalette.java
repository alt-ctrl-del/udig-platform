/**
 * uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004-2012, Refractions Research Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Refractions BSD
 * License v1.0 (http://udig.refractions.net/files/bsd3-v10.html).
 */
package org.locationtech.udig.project.ui.internal;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.ui.palette.FlyoutPaletteComposite.FlyoutPreferences;
import org.eclipse.gef.ui.parts.GraphicalEditorWithFlyoutPalette;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IconAndMessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.SubActionBars2;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.locationtech.udig.catalog.ITransientResolve;
import org.locationtech.udig.core.internal.ExtensionPointList;
import org.locationtech.udig.internal.ui.IDropTargetProvider;
import org.locationtech.udig.internal.ui.UDIGControlDropListener;
import org.locationtech.udig.internal.ui.UDIGDropHandler;
import org.locationtech.udig.project.EditManagerEvent;
import org.locationtech.udig.project.IEditManagerListener;
import org.locationtech.udig.project.ILayer;
import org.locationtech.udig.project.ILayerListener;
import org.locationtech.udig.project.IMapCompositionListener;
import org.locationtech.udig.project.IMapListener;
import org.locationtech.udig.project.LayerEvent;
import org.locationtech.udig.project.MapCompositionEvent;
import org.locationtech.udig.project.MapEvent;
import org.locationtech.udig.project.interceptor.MapInterceptor;
import org.locationtech.udig.project.internal.Layer;
import org.locationtech.udig.project.internal.Map;
import org.locationtech.udig.project.internal.Project;
import org.locationtech.udig.project.internal.ProjectPackage;
import org.locationtech.udig.project.internal.ProjectPlugin;
import org.locationtech.udig.project.internal.render.RenderManager;
import org.locationtech.udig.project.render.IViewportModelListener;
import org.locationtech.udig.project.render.ViewportModelEvent;
import org.locationtech.udig.project.ui.AnimationUpdater;
import org.locationtech.udig.project.ui.ApplicationGIS;
import org.locationtech.udig.project.ui.IAnimation;
import org.locationtech.udig.project.ui.UDIGEditorInput;
import org.locationtech.udig.project.ui.commands.IDrawCommand;
import org.locationtech.udig.project.ui.controls.ScaleRatioLabel;
import org.locationtech.udig.project.ui.internal.commands.draw.DrawFeatureCommand;
import org.locationtech.udig.project.ui.render.displayAdapter.ViewportPane;
import org.locationtech.udig.project.ui.tool.IMapEditorSelectionProvider;
import org.locationtech.udig.project.ui.tool.IToolManager;
import org.locationtech.udig.project.ui.viewers.MapEditDomain;
import org.locationtech.udig.project.ui.viewers.MapViewer;
import org.locationtech.udig.ui.IBlockingSelection;
import org.locationtech.udig.ui.PlatformGIS;
import org.locationtech.udig.ui.PreShutdownTask;
import org.locationtech.udig.ui.ShutdownTaskList;
import org.locationtech.udig.ui.UDIGDragDropUtilities;
import org.locationtech.udig.ui.UDIGDragDropUtilities.DragSourceDescriptor;
import org.locationtech.udig.ui.UDIGDragDropUtilities.DropTargetDescriptor;
import org.opengis.feature.simple.SimpleFeature;

/**
 * This class is the Eclipse editor Part in which a ViewportPane is embedded. The ViewportPane
 * displays and edits Maps. MapViewport is used to initialize ViewportPane and the RenderManager.
 * <p>
 * Note:
 * <ul>
 * <li>The super class GraphicalEditorWithFlyoutPalette will smoothly switch between displaying the
 * palette inline; and hiding it when the normal PaletteView is opened</li>
 * </ul>
 *
 * @author Jesse Eichar
 * @version $Revision: 1.9 $
 */
// TODO: Rename this to MapEditor to prevent code bloat / code duplication
public class MapEditorWithPalette extends GraphicalEditorWithFlyoutPalette
        implements IDropTargetProvider, IAdaptable, MapEditorPart {

    public static final int STATUS_LINE_HEIGHT;
    static {
        if (Platform.getWS().equals(Platform.WS_WIN32)) {
            STATUS_LINE_HEIGHT = 24;
        } else {
            STATUS_LINE_HEIGHT = 32;
        }
    }

    /** The id of the MapViewport View */
    public static final String ID = "org.locationtech.udig.project.ui.mapEditor"; //$NON-NLS-1$

    /**
     * This is responsible for tracking the active tool; it is a facility provided by GEF. We are
     * not using GEF tools directly; simply borrowing some of their infrastructure to support a nice
     * visual palette.
     * <p>
     * The *id* of the current tool in the editDomain is used to determine the active tool for the
     * map.
     * <p>
     * Holds onto the paletteViewer while delegating to ToolManager
     */
    private MapEditDomain editDomain;

    final StatusLineManager statusLineManager = new StatusLineManager();

    private MapSite mapSite;

    private boolean dirty = false;

    private PaletteRoot paletteRoot;

    private MapViewer viewer = null;

    private IContributionItem crsContributionItem;

    private IContributionItem scaleContributionItem;

    /**
     * This composite is used to hold the view; and the status line.
     */
    private Composite composite;

    private DropTargetDescriptor dropTarget;

    /** This is for testing only DO NOT USE OTHERWISE */
    public boolean isTesting;

    private DragSourceDescriptor dragSource;

    /**
     * Creates a new MapViewport object.
     */
    public MapEditorWithPalette() {
        // Make sure the featureEditorProcessor has been started.
        // This will load all the tools so we can use them
        ProjectUIPlugin.getDefault().getFeatureEditProcessor();
    }

    @Override
    public Composite getComposite() {
        return composite;
    }

    @Override
    protected PaletteRoot getPaletteRoot() {
        if (paletteRoot == null) {
            paletteRoot = MapToolPaletteFactory.createPalette();
        }
        return paletteRoot;
    }

    @Override
    protected FlyoutPreferences getPalettePreferences() {
        return MapToolPaletteFactory.createPalettePreferences();
    }

    /**
     * The layer listener will listen for edit events and mark the map as dirty if the layer is
     * modified.
     */
    ILayerListener layerListener = new ILayerListener() {

        @Override
        public void refresh(LayerEvent event) {
            if (event.getType() == LayerEvent.EventType.EDIT_EVENT) {
                setDirty(true);
                event.getSource().getBlackboard().put(LAYER_DIRTY_KEY, "true"); //$NON-NLS-1$
            }
        }

    };

    /**
     * This listener is called when layers are added and removed.
     * <p>
     * This method will add the layerListener to each layer on the map.
     * </p>
     */
    IMapCompositionListener mapCompositionListener = new IMapCompositionListener() {

        @Override
        @SuppressWarnings("unchecked")
        public void changed(MapCompositionEvent event) {
            switch (event.getType()) {
            case MANY_ADDED:
                Collection<ILayer> added = (Collection<ILayer>) event.getNewValue();
                for (ILayer layer : added) {
                    layer.addListener(layerListener);
                }
                break;
            case MANY_REMOVED:
                Collection<ILayer> removed = (Collection<ILayer>) event.getOldValue();
                for (ILayer layer : removed) {
                    removeListenerFromLayer(event, layer);
                }
                break;
            case ADDED:

                ((ILayer) event.getNewValue()).addListener(layerListener);
                break;
            case REMOVED:

                ILayer removedLayer = ((ILayer) event.getOldValue());
                removeListenerFromLayer(event, removedLayer);
                break;
            default:
                break;
            }

            final boolean isMapDirty = isMapDirty();

            if (isMapDirty != isDirty()) {
                setDirty(isMapDirty);
            }
        }

        private void removeListenerFromLayer(MapCompositionEvent event, ILayer removedLayer) {
            removedLayer.removeListener(layerListener);
            setDirty(isMapDirty());
        }

    };

    /**
     * Listens to the Map and will change the editor title if the map name is changed. Also marks a
     * layer as dirty if the edit manager has some kind of event.
     */
    IMapListener mapListener = new IMapListener() {

        @Override
        public void changed(final MapEvent event) {
            if (composite == null)
                return; // the composite hasn't been created so chill out
            if (getMap() == null || composite.isDisposed()) {
                event.getSource().removeMapListener(this);
                return;
            }

            MapEditorWithPalette.this.composite.getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    switch (event.getType()) {
                    case NAME:
                        setPartName((String) event.getNewValue()); // rename the map
                        break;
                    case EDIT_MANAGER:
                        for (ILayer layer : event.getSource().getMapLayers()) {
                            if (layer.hasResource(ITransientResolve.class)) {
                                setDirty(true);
                                break;
                            }
                        }
                        break;
                    default:
                        break;
                    }
                }
            });
        }

    };

    /**
     * Listens to the edit manager; will clear the dirty status after a commit or after a rollback
     * (as long as a map does not have temporary layers).
     */
    IEditManagerListener editListener = new IEditManagerListener() {

        @Override
        public void changed(EditManagerEvent event) {
            switch (event.getType()) {
            case EditManagerEvent.POST_COMMIT:
                if (!hasTemporaryLayers()) {
                    setDirty(false);
                }
                break;
            case EditManagerEvent.POST_ROLLBACK:
                if (!hasTemporaryLayers()) {
                    setDirty(false);
                }
                break;
            default:
                break;
            }
        }

        private boolean hasTemporaryLayers() {
            if (getMap() == null)
                return false;
            List<Layer> layers = getMap().getLayersInternal();
            if (layers == null)
                return false;
            for (Layer layer : layers) {
                if (layer.hasResource(ITransientResolve.class)) {
                    return true;
                }
            }
            return false;
        }
    };

    private LayerSelectionListener layerSelectionListener;

    private ReplaceableSelectionProvider replaceableSelectionProvider;

    private PreShutdownTask shutdownTask = new PreShutdownTask() {

        @Override
        public int getProgressMonitorSteps() {
            return 3;
        }

        @Override
        public boolean handlePreShutdownException(Throwable t, boolean forced) {
            ProjectUIPlugin.log("error prepping map editors for shutdown", t); //$NON-NLS-1$
            return true;
        }

        @Override
        public boolean preShutdown(IProgressMonitor monitor, IWorkbench workbench, boolean forced)
                throws Exception {
            monitor.beginTask("Saving Map Editor", 3);
            save(SubMonitor.convert(monitor, 1));
            if (dirty) {
                if (!forced) {
                    return false;
                } else {
                    setDirty(false);
                }
            }
            removeTemporaryLayers(ProjectPlugin.getPlugin().getPreferenceStore());
            monitor.worked(1);

            /**
             * Save the map's URI in the preferences so that it will be loaded the next time uDig is
             * run.
             */
            Resource resource = getMap().eResource();
            if (resource != null) {
                // save editor
                try {
                    IPreferenceStore p = ProjectUIPlugin.getDefault().getPreferenceStore();
                    int numEditors = p.getInt(ID);
                    String id = ID + ":" + numEditors; //$NON-NLS-1$
                    numEditors++;
                    p.setValue(ID, numEditors);
                    String value = resource.getURI().toString();
                    p.setDefault(id, ""); //$NON-NLS-1$
                    p.setValue(id, value);
                } catch (Exception e) {
                    ProjectUIPlugin.log("Failure saving which maps are open", e); //$NON-NLS-1$
                }
            }

            monitor.worked(1);
            monitor.done();

            return true;
        }

        private void save(final IProgressMonitor monitor) {
            if (dirty) {
                PlatformGIS.syncInDisplayThread(new Runnable() {
                    @Override
                    public void run() {
                        IconAndMessageDialog d = new SaveDialog(
                                Display.getCurrent().getActiveShell(), getMap());
                        int result = d.open();

                        if (result == IDialogConstants.YES_ID)
                            doSave(monitor);
                        else if (result != Window.CANCEL) {
                            setDirty(false);
                        }
                    }
                });
            }
        }

    };

    @Override
    public Object getAdapter(Class adaptee) {
        if (adaptee.isAssignableFrom(Map.class)) {
            return getMap();
        }
        if (adaptee.isAssignableFrom(ViewportPane.class)) {
            return viewer.getViewport();
        }
        return super.getAdapter(adaptee);
    }

    private void clearLayerDirtyFlag() {
        List<ILayer> layers = getMap().getMapLayers();
        for (ILayer layer : layers) {
            layer.getBlackboard().put(LAYER_DIRTY_KEY, null);
        }
    }

    @Override
    public void setFont(Control control) {
        viewer.setFont(control);
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#dispose()
     */
    @Override
    public void dispose() {

        if (isTesting)
            return;
        if (getSite() == null || getSite().getPage() == null) {
            // Exception occurred before instantiating editor
            return;
        }

        final IWorkbenchPage page = getSite().getPage();

        page.removePostSelectionListener(layerSelectionListener);
        runMapClosingInterceptors();

        deregisterFeatureFlasher();
        page.removePartListener(partlistener);
        if (viewer != null) {
            viewer.getViewport().removePaneListener(getMap().getViewportModelInternal());
        }
        getMap().getViewportModelInternal().setInitialized(false);

        selectFeatureListener = null;
        partlistener = null;

        statusLineManager.dispose();

        MapToolPaletteFactory.dispose(paletteRoot);
        paletteRoot = null;

        final ScopedPreferenceStore store = ProjectPlugin.getPlugin().getPreferenceStore();
        if (!PlatformUI.getWorkbench().isClosing()) {
            ShutdownTaskList.instance().removePreShutdownTask(shutdownTask);
            try {
                // kill rending now - even if it is moving
                getRenderManager().dispose();
            } catch (Throwable t) {
                ProjectUIPlugin.log("Shutting down rendering - " + t, null); //$NON-NLS-1$
            }
            getMap().getEditManagerInternal().setEditFeature(null, null);
            try {
                PlatformGIS.run(new ISafeRunnable() {
                    @Override
                    public void handleException(Throwable exception) {
                        ProjectUIPlugin.log("error saving map: " + getMap().getName(), exception); //$NON-NLS-1$
                    }

                    @Override
                    public void run() throws Exception {

                        removeTemporaryLayers(store);
                        Project p = getMap().getProjectInternal();
                        if (p != null) {
                            if (p.eResource() != null && p.eResource().isModified()) {
                                p.eResource().save(ProjectPlugin.getPlugin().saveOptions);
                            }

                            /**
                             * When closing a map the platform wants to save the map resource, but
                             * if you are removing the map, its no longer available.
                             */
                            final Map map = getMap();
                            final Resource resource = map.eResource();
                            if (resource != null)
                                resource.save(ProjectPlugin.getPlugin().saveOptions);

                            // need to kick the Project so viewers will update
                            p.eNotify(new ENotificationImpl((InternalEObject) p, Notification.SET,
                                    ProjectPackage.PROJECT__ELEMENTS_INTERNAL, null, null));

                        } else {
                            final Resource resource = getMap().eResource();
                            if (resource != null)
                                resource.save(ProjectPlugin.getPlugin().saveOptions);
                        }
                        if (viewer != null) {
                            viewer.dispose();
                            viewer = null;
                        }
                    }

                });
            } catch (Exception e) {
                ProjectPlugin.log("Exception while saving Map", e); //$NON-NLS-1$
            }
        }

        super.dispose();

    }

    private void runMapClosingInterceptors() {
        List<IConfigurationElement> interceptors = ExtensionPointList
                .getExtensionPointList(MapInterceptor.MAP_INTERCEPTOR_EXTENSIONPOINT);
        for (IConfigurationElement element : interceptors) {
            if (!MapInterceptor.CLOSING_ID.equals(element.getName())) // $NON-NLS-1$
                continue;
            try {
                MapInterceptor interceptor = (MapInterceptor) element
                        .createExecutableExtension("class"); //$NON-NLS-1$
                interceptor.run(getMap());
            } catch (Exception e) {
                ProjectPlugin.log("", e); //$NON-NLS-1$
            }
        }
    }

    private void removeTemporaryLayers(IPreferenceStore store) {
        if (store.getBoolean(
                org.locationtech.udig.project.preferences.PreferenceConstants.P_REMOVE_LAYERS)) {
            List<Layer> layers = getMap().getLayersInternal();
            List<Layer> layersToRemove = new ArrayList<>();
            for (Layer layer : layers) {
                if (!layer.getGeoResources().isEmpty()
                        && layer.getGeoResources().get(0).canResolve(ITransientResolve.class)) {
                    layersToRemove.add(layer);
                }
            }

            if (!layers.isEmpty()) {
                if (getMap().eResource() != null)
                    getMap().eResource().setModified(true);
                layers.removeAll(layersToRemove);
            }
        }
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        final boolean[] success = new boolean[] { false };

        PlatformGIS.syncInDisplayThread(new SaveMapPaletteRunnable(this, success));

        if (success[0]) {
            setDirty(false);
        } else {
            // abort shutdown if in progress
            monitor.setCanceled(true);
        }
    }

    @Override
    public void doSaveAs() {
        throw new UnsupportedOperationException("Do Save As is not implemented yet"); //$NON-NLS-1$
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) {
        setSite(site);
        setInput(input);
        // initialize ToolManager
        ApplicationGIS.getToolManager();
    }

    @Override
    protected void setInput(IEditorInput input) {
        if (getEditorInput() != null) {
            Map map = (Map) ((UDIGEditorInput) getEditorInput()).getProjectElement();
            if (viewer != null) {
                viewer.setMap(null);
            }
            map.removeMapCompositionListener(mapCompositionListener);
            map.removeMapListener(mapListener);
            map.getEditManager().removeListener(editListener);
        }
        super.setInput(input);
        if (input != null) {
            if (viewer != null) {
                viewer.setMap((Map) ((UDIGEditorInput) input).getProjectElement());
            }
            getMap().addMapCompositionListener(mapCompositionListener);
            getMap().addMapListener(mapListener);
            getMap().getEditManager().addListener(editListener);
        }

    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    private boolean isMapDirty() {
        boolean mapIsDirty = false;
        for (ILayer layer : getMap().getMapLayers()) {
            if (layer.hasResource(ITransientResolve.class)) {
                mapIsDirty = true;
            }
            boolean layerIsDirty = layer.getBlackboard().get(LAYER_DIRTY_KEY) != null;
            if (layerIsDirty) {
                mapIsDirty = true;
            }
        }
        return mapIsDirty;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    @Override
    public void setDirty(boolean dirty) {
        if (dirty == this.dirty)
            return;

        this.dirty = dirty;

        if (!dirty) {
            clearLayerDirtyFlag();
        }

        Display.getDefault().asyncExec(new Runnable() {
            /**
             * @see java.lang.Runnable#run()
             */
            @Override
            public void run() {
                firePropertyChange(PROP_DIRTY);
            }
        });
    }

    @Override
    public boolean isSaveOnCloseNeeded() {
        return true;
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public void createPartControl(final Composite parent) {
        ShutdownTaskList.instance().addPreShutdownTask(shutdownTask);
        if (editDomain == null) {
            editDomain = new MapEditDomain(this);
        }
        setEditDomain(editDomain);

        // super class sets up the splitter; it needs the setEditDomain to be defined
        // prior to the method being called (so the FlyoutPaletteComposite split can latch on)

        super.createPartControl(parent);
        // the above sets up a splitter; and then calls GraphicalEditor.createGraphicalViewer
        // which we can use to set up our display area...
    }

    @Override
    protected Control getGraphicalControl() {
        return composite;
    }

    /**
     * Hijacked; supposed to create a GraphicalViewer on the specified <code>Composite</code>.
     * <p>
     * Instead we make use of the composite for our MapViewer.
     *
     * @param parent the parent composite
     */
    @Override
    protected void createGraphicalViewer(Composite parent) {
        composite = new Composite(parent, SWT.NO_BACKGROUND);

        composite.setLayout(new FormLayout());
        composite.setFont(parent.getFont());
        setPartName(getMap().getName());

        setTitleToolTip(Messages.MapEditor_titleToolTip);
        setTitleImage(ProjectUIPlugin.getDefault().getImage(ISharedImages.MAP_OBJ));

        viewer = new MapViewer(composite, this, SWT.DOUBLE_BUFFERED);
        // we need an edit domain for GEF
        // This represents the "Current Tool" for the Palette
        // We should not duplicate the idea of current tools so we may
        // need to delegate to getEditDomain; and just use the MapEditTool *id*
        // editDomain = new MapEditDomain(this);
        // setEditDomain( editDomain );

        // allow the viewer to open our context menu; work with our selection provider etc
        viewer.init(this);

        // if a map was provided as input we can ask the viewer to use it
        Map input = (Map) ((UDIGEditorInput) getEditorInput()).getProjectElement();
        if (input != null) {
            viewer.setMap(input);
        }

        FormData formdata = new FormData();
        formdata.top = new FormAttachment(0);
        formdata.bottom = new FormAttachment(100, -STATUS_LINE_HEIGHT);
        formdata.left = new FormAttachment(0);
        formdata.right = new FormAttachment(100);
        viewer.getViewport().getControl().setLayoutData(formdata);

        statusLineManager.add(new GroupMarker(StatusLineManager.BEGIN_GROUP));
        statusLineManager.add(new GroupMarker(StatusLineManager.MIDDLE_GROUP));
        statusLineManager.add(new GroupMarker(StatusLineManager.END_GROUP));
        statusLineManager.createControl(composite, SWT.BORDER);
        formdata = new FormData();
        formdata.left = new FormAttachment(0);
        formdata.right = new FormAttachment(100);
        formdata.top = new FormAttachment(viewer.getViewport().getControl(), 0, SWT.BOTTOM);
        formdata.bottom = new FormAttachment(100);
        statusLineManager.getControl().setLayoutData(formdata);

        getSite().getPage().addPartListener(partlistener);
        registerFeatureFlasher();
        viewer.getViewport().addPaneListener(getMap().getViewportModelInternal());

        layerSelectionListener = new LayerSelectionListener(
                new MapLayerSelectionCallback(getMap(), composite));

        getSite().getPage().addPostSelectionListener(layerSelectionListener);

        for (Layer layer : getMap().getLayersInternal()) {
            layer.addListener(layerListener);
        }

        dropTarget = UDIGDragDropUtilities.addDropSupport(viewer.getViewport().getControl(), this);
        this.replaceableSelectionProvider = new ReplaceableSelectionProvider();
        getSite().setSelectionProvider(replaceableSelectionProvider);
        runMapOpeningInterceptor(getMap());
        mapSite = new MapSite(super.getSite(), this);
        final IContributionManager statusBar = mapSite.getActionBars().getStatusLineManager();

        scaleContributionItem = new ScaleRatioLabel(this);
        scaleContributionItem.setVisible(true);
        statusBar.appendToGroup(StatusLineManager.END_GROUP, scaleContributionItem);

        crsContributionItem = new CRSContributionItem(this);
        crsContributionItem.setVisible(true);
        statusBar.appendToGroup(StatusLineManager.END_GROUP, crsContributionItem);

        scaleContributionItem.update();
        crsContributionItem.update();
        statusBar.update(true);

        getMap().getViewportModel().addViewportModelListener(new IViewportModelListener() {

            @Override
            public void changed(ViewportModelEvent event) {
                if (getMap() == null) {
                    event.getSource().removeViewportModelListener(this);
                    return;
                }
            }

        });

        setDirty(isMapDirty());
    }

    private void runMapOpeningInterceptor(Map map) {
        List<IConfigurationElement> interceptors = ExtensionPointList
                .getExtensionPointList(MapInterceptor.MAP_INTERCEPTOR_EXTENSIONPOINT);
        for (IConfigurationElement element : interceptors) {
            if (!MapInterceptor.OPENING_ID.equals(element.getName())) // $NON-NLS-1$
                continue;
            try {
                MapInterceptor interceptor = (MapInterceptor) element
                        .createExecutableExtension("class"); //$NON-NLS-1$
                interceptor.run(map);
            } catch (Exception e) {
                ProjectPlugin.log("", e); //$NON-NLS-1$
            }
        }
    }

    private FlashFeatureListener selectFeatureListener = new FlashFeatureListener();

    private boolean flashFeatureRegistered = false;

    private Action propertiesAction;

    /**
     * registers a listener with the current page that flashes a feature each time the current
     * selected feature changes.
     */
    protected synchronized void registerFeatureFlasher() {
        if (!flashFeatureRegistered) {
            flashFeatureRegistered = true;
            IWorkbenchPage page = getSite().getPage();
            page.addPostSelectionListener(selectFeatureListener);
        }
    }

    protected synchronized void deregisterFeatureFlasher() {
        flashFeatureRegistered = false;
        getSite().getPage().removePostSelectionListener(selectFeatureListener);
    }

    void createContextMenu() {
        Menu menu;
        menu = viewer.getMenu();
        if (menu == null) {
            final MenuManager contextMenu = new MenuManager();
            contextMenu.setRemoveAllWhenShown(true);
            contextMenu.addMenuListener(new IMenuListener() {
                @Override
                public void menuAboutToShow(IMenuManager mgr) {
                    IToolManager tm = ApplicationGIS.getToolManager();

                    contextMenu.add(tm.getENTERAction());
                    contextMenu.add(new Separator());

                    contextMenu.add(tm.getZOOMTOSELECTEDAction());
                    contextMenu.add(new Separator());
                    contextMenu.add(tm.getBACKWARD_HISTORYAction());
                    contextMenu.add(tm.getFORWARD_HISTORYAction());
                    contextMenu.add(new Separator());
                    contextMenu.add(tm.getCOPYAction(MapEditorWithPalette.this));
                    contextMenu.add(tm.getPASTEAction(MapEditorWithPalette.this));
                    contextMenu.add(tm.getDELETEAction());

                    /**
                     * Gets contributions from active modal tool if possible
                     */
                    tm.contributeActiveModalTool(contextMenu);

                    contextMenu.add(new Separator());
                    contextMenu.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
                    if (getMap().getEditManager().getEditFeature() != null) {
                        contextMenu.add(ProjectUIPlugin.getDefault().getFeatureEditProcessor()
                                .getEditFeatureAction(
                                        getSite().getSelectionProvider().getSelection()));

                        contextMenu.add(ProjectUIPlugin.getDefault().getFeatureEditProcessor()
                                .getEditWithFeatureMenu(
                                        getSite().getSelectionProvider().getSelection()));
                    }
                    contextMenu.add(ApplicationGIS.getToolManager().createOperationsContextMenu(
                            replaceableSelectionProvider.getSelection()));
                    contextMenu.add(new Separator());
                    contextMenu.add(ActionFactory.EXPORT.create(getSite().getWorkbenchWindow()));
                    contextMenu.add(new Separator());
                    contextMenu.add(getPropertiesAction());
                }
            });

            // Create menu.
            menu = contextMenu.createContextMenu(composite);
            viewer.setMenu(menu);
            getSite().registerContextMenu(contextMenu, getSite().getSelectionProvider());
        }
    }

    protected IAction getPropertiesAction() {
        if (propertiesAction == null) {
            final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            final PropertyDialogAction tmp = new PropertyDialogAction(new SameShellProvider(shell),
                    new ISelectionProvider() {

                        @Override
                        public void addSelectionChangedListener(
                                ISelectionChangedListener listener) {
                        }

                        @Override
                        public ISelection getSelection() {
                            return new StructuredSelection(getMap());
                        }

                        @Override
                        public void removeSelectionChangedListener(
                                ISelectionChangedListener listener) {
                        }

                        @Override
                        public void setSelection(ISelection selection) {

                        }

                    });

            propertiesAction = new Action() {
                @Override
                public void runWithEvent(Event event) {
                    tmp.createDialog().open();
                }
            };

            propertiesAction.setText(tmp.getText());
            propertiesAction.setActionDefinitionId(tmp.getActionDefinitionId());
            propertiesAction.setDescription(tmp.getDescription());
            propertiesAction.setHoverImageDescriptor(tmp.getHoverImageDescriptor());
            propertiesAction.setImageDescriptor(tmp.getImageDescriptor());
            propertiesAction.setToolTipText(tmp.getToolTipText());

        }
        getEditorSite().getActionBars().setGlobalActionHandler(ActionFactory.PROPERTIES.getId(),
                propertiesAction);
        return propertiesAction;
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#setFocus()
     */
    @Override
    public void setFocus() {
        composite.setFocus();
        if (crsContributionItem != null) {
            crsContributionItem.update();
        }
        if (scaleContributionItem != null) {
            scaleContributionItem.update();
        }
    }

    /**
     * Returns the map that this editor edits
     *
     * @return Returns the map that this editor edits
     */
    @Override
    public Map getMap() {
        UDIGEditorInput editorInput = (UDIGEditorInput) getEditorInput();
        if (editorInput != null) {
            return (Map) editorInput.getProjectElement();
        } else {
            return null;
        }
    }

    /**
     * Returns the ActionbarContributor for the Editor.
     *
     * @return the ActionbarContributor for the Editor.
     */
    public SubActionBars2 getActionbar() {
        return (SubActionBars2) getEditorSite().getActionBars();
    }

    IPartListener2 partlistener = new IPartListener2() {
        @Override
        public void partActivated(IWorkbenchPartReference partRef) {
            if (partRef.getPart(false) == MapEditorWithPalette.this) {
                registerFeatureFlasher();
                IToolManager tools = ApplicationGIS.getToolManager();
                tools.setCurrentEditor(MapEditorWithPalette.this);
            }
        }

        @Override
        public void partBroughtToTop(IWorkbenchPartReference partRef) {

        }

        @Override
        public void partClosed(IWorkbenchPartReference partRef) {
            if (partRef.getPart(false) == MapEditorWithPalette.this) {
                deregisterFeatureFlasher();
                visible = false;
            }
        }

        @Override
        public void partDeactivated(IWorkbenchPartReference partRef) {
            // do nothing
        }

        @Override
        public void partOpened(IWorkbenchPartReference partRef) {
            // do nothing
        }

        @Override
        public void partHidden(IWorkbenchPartReference partRef) {
            if (partRef.getPart(false) == MapEditorWithPalette.this) {
                deregisterFeatureFlasher();
                visible = false;
            }
        }

        @Override
        public void partVisible(IWorkbenchPartReference partRef) {
            if (partRef.getPart(false) == MapEditorWithPalette.this) {
                registerFeatureFlasher();
                visible = true;
            }
        }

        @Override
        public void partInputChanged(IWorkbenchPartReference partRef) {

        }

    };

    private boolean draggingEnabled;

    private volatile boolean visible = false;

    /**
     * Opens the map's context menu.
     */
    @Override
    public void openContextMenu() {
        viewer.openContextMenu();
    }

    @Override
    public UDIGDropHandler getDropHandler() {
        return ((UDIGControlDropListener) dropTarget.listener).getHandler();
    }

    @Override
    public Object getTarget(DropTargetEvent event) {
        return this;
    }

    /**
     * Enables or disables dragging (drag and drop) from the map editor.
     */
    @Override
    public void setDragging(boolean enable) {
        if (draggingEnabled == enable)
            return;
        if (enable) {
            dragSource = UDIGDragDropUtilities.addDragSupport(viewer.getViewport().getControl(),
                    getSite().getSelectionProvider());
        } else {
            dragSource.source.dispose();
        }
        draggingEnabled = enable;
    }

    @Override
    public boolean isDragging() {
        return draggingEnabled;
    }

    @Override
    public String toString() {
        return getTitle();
    }

    @Override
    public void setSelectionProvider(IMapEditorSelectionProvider selectionProvider) {
        if (selectionProvider == null) {
            throw new NullPointerException("selection provider must not be null!"); //$NON-NLS-1$
        }
        selectionProvider.setActiveMap(getMap(), this);
        if (selectionProvider != this.replaceableSelectionProvider.getSelectionProvider()) {
            this.replaceableSelectionProvider.setProvider(selectionProvider);
        }
        createContextMenu();
    }

    @Override
    public MapSite getMapSite() {
        return mapSite;
    }

    private class FlashFeatureListener implements ISelectionListener {

        @Override
        public void selectionChanged(IWorkbenchPart part, final ISelection selection) {
            if (part == MapEditorWithPalette.this || getSite().getPage().getActivePart() != part
                    || selection instanceof IBlockingSelection)
                return;

            ISafeRunnable sendAnimation = new ISafeRunnable() {
                @Override
                public void run() {
                    if (selection instanceof IStructuredSelection) {
                        IStructuredSelection s = (IStructuredSelection) selection;
                        List<SimpleFeature> features = new ArrayList<>();
                        for (Iterator iter = s.iterator(); iter.hasNext();) {
                            Object element = iter.next();

                            if (element instanceof SimpleFeature) {
                                SimpleFeature feature = (SimpleFeature) element;
                                features.add(feature);
                            } else if (element instanceof IAdaptable) {
                                final IAdaptable adaptable = (IAdaptable) element;
                                final Object featureObj = adaptable.getAdapter(SimpleFeature.class);
                                if (featureObj != null && featureObj instanceof SimpleFeature) {
                                    final SimpleFeature feature = (SimpleFeature) featureObj;
                                    features.add(feature);
                                }
                            }
                        }
                        if (features.isEmpty()) {
                            return;
                        }
                        if (!getRenderManager().isDisposed()) {
                            IAnimation anim = createAnimation(features);
                            if (anim != null)
                                AnimationUpdater.runTimer(
                                        getMap().getRenderManager().getMapDisplay(), anim);
                        }
                    }
                }

                @Override
                public void handleException(Throwable exception) {
                    ProjectUIPlugin.log("Exception preparing animation", exception); //$NON-NLS-1$
                }
            };

            try {
                sendAnimation.run();
            } catch (Exception e) {
                ProjectUIPlugin.log("", e); //$NON-NLS-1$
            }
            // PlatformGIS.run(sendAnimation);
        }

        private IAnimation createAnimation(List<SimpleFeature> current) {
            final List<IDrawCommand> commands = new ArrayList<>();
            for (SimpleFeature feature : current) {
                if (feature == null || feature.getFeatureType().getGeometryDescriptor() == null)
                    continue;
                DrawFeatureCommand command = null;
                if (feature instanceof IAdaptable) {
                    Layer layer = ((IAdaptable) feature).getAdapter(Layer.class);
                    if (layer != null)
                        try {
                            command = new DrawFeatureCommand(feature, layer);
                        } catch (IOException e) {
                            // do nothing... thats life
                        }
                }
                if (command == null) {
                    command = new DrawFeatureCommand(feature);
                }
                command.setMap(getMap());
                command.preRender();
                commands.add(command);
            }
            Rectangle2D rect = new Rectangle();
            final Rectangle validArea = (Rectangle) rect;

            return new FeatureAnimation(commands, validArea);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    RenderManager getRenderManager() {
        return viewer.getRenderManager();
    }

    @Override
    public IStatusLineManager getStatusLineManager() {
        return statusLineManager;
    }

    @Override
    public MapEditDomain getEditDomain() {
        return editDomain;
    }

    @Override
    public boolean isTesting() {
        return this.isTesting;
    }

    @Override
    public void setTesting(boolean testing) {
        this.isTesting = testing;
    }
}
