/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.icon.mindmapmode;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.components.IPropertyControl;
import org.freeplane.core.resources.components.IPropertyControlCreator;
import org.freeplane.core.resources.components.KeyProperty;
import org.freeplane.core.resources.components.OptionPanelBuilder;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IndexedTree;
import org.freeplane.core.ui.LengthUnits;
import org.freeplane.core.ui.MenuSplitter;
import org.freeplane.core.ui.components.FreeplaneToolBar;
import org.freeplane.core.ui.components.JAutoScrollBarPane;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.components.resizer.CollapseableBoxBuilder;
import org.freeplane.core.ui.components.resizer.JResizer.Direction;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryAccessor;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor.Phase;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.util.Quantity;
import org.freeplane.features.filter.condition.ICondition;
import org.freeplane.features.icon.IIconInformation;
import org.freeplane.features.icon.IconContainedCondition;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.IconExistsCondition;
import org.freeplane.features.icon.IconGroup;
import org.freeplane.features.icon.IconStore;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.IExtensionCopier;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.styles.ConditionPredicate;
import org.freeplane.features.styles.LogicalStyleController;
import org.freeplane.features.ui.FrameController;

/**
 * @author Dimitry Polivaev
 */
public class MIconController extends IconController {
	private static final Insets ICON_SUBMENU_INSETS = new Insets(3, 0, 3, 0);
	private static final int ARROW_SIZE = Math.round(UITools.getUIFontSize(0.8));
	private static final Font ARROW_FONT = new Font("SansSerif", 0, ARROW_SIZE);
	private static final ConditionPredicate DEPENDS_ON_ICON = new ConditionPredicate() {

		@Override
		public boolean test(ICondition condition) {
			return condition instanceof IconContainedCondition
					|| condition instanceof IconExistsCondition;
		}
	};

	private final class IconActionBuilder implements EntryVisitor {
		private final HashMap<String, Entry> submenuEntries = new HashMap<String, Entry>();
		final private ModeController modeController;

		public IconActionBuilder(ModeController modeController) {
			this.modeController = modeController;
		}

		@Override
		public void visit(Entry target) {
			addIcons(target);
			submenuEntries.clear();
			updateIconToolbar(modeController);
		}

		private void addIcons(final Entry target) {
			for (final IconGroup iconGroup : STORE.getGroups()) {
				addIconGroup(target, iconGroup);
			}
		}

		private void addIconGroup(final Entry target, final IconGroup group) {
			if (group.getIcons().size() < 1) {
				return;
			}
			final Entry item = new Entry();
			EntryAccessor entryAccessor = new EntryAccessor();
			entryAccessor.setIcon(item, group.getGroupIcon().getIcon());
			item.setName(group.getDescriptionKey());
			target.addChild(item);
			for (final MindIcon icon : group.getIcons()) {
				final String fileName = icon.getFileName();
				addAction(item, "", icon, fileName);
			}
		}

		private void addAction(final Entry target, final String itemKey, final MindIcon icon,
		                       final String fileName) {
			final int separatorPosition = fileName.indexOf('/');
			EntryAccessor entryAccessor = new EntryAccessor();
			if (separatorPosition == -1) {
				entryAccessor.addChildAction(target, iconActions.get(icon));
			}
			else {
				final String submenuName = fileName.substring(0, separatorPosition);
				final String submenuKey = itemKey + "/" + submenuName;
				Entry submenu = submenuEntries.get(submenuKey);
				if (submenu == null) {
					submenu = new Entry();
					entryAccessor.setText(submenu, submenuName);
					submenuEntries.put(submenuKey, submenu);
					target.addChild(submenu);
				}
				addAction(submenu, submenuKey, icon, fileName.substring(separatorPosition + 1));
			}
		}
		@Override
		public boolean shouldSkipChildren(Entry entry) {
			return false;
		}
	}

	public static enum Keys {
		ICONS
	};

	private static class ExtensionCopier implements IExtensionCopier {
		@Override
		public void copy(final Object key, final NodeModel from, final NodeModel to) {
			if (!key.equals(Keys.ICONS)) {
				return;
			}
			copy(from, to);
		}

		public void copy(final NodeModel from, final NodeModel to) {
			final List<MindIcon> sourceIcons = from.getIcons();
			final List<MindIcon> targetIcons = to.getIcons();
			for (final MindIcon icon : sourceIcons) {
				if (targetIcons.contains(icon)) {
					continue;
				}
				to.addIcon(icon);
			}
		}

		@Override
		public void remove(final Object key, final NodeModel from) {
			if (!key.equals(Keys.ICONS)) {
				return;
			}
			while (from.removeIcon() > 0) {
				;
			}
		}

		@Override
		public void remove(final Object key, final NodeModel from, final NodeModel which) {
			if (!key.equals(Keys.ICONS)) {
				return;
			}
			final List<MindIcon> targetIcons = from.getIcons();
			final List<MindIcon> whichIcons = which.getIcons();
			final Iterator<MindIcon> targetIconIterator = targetIcons.iterator();
			while (targetIconIterator.hasNext()) {
				MindIcon icon = targetIconIterator.next();
				if (!whichIcons.contains(icon)) {
					continue;
				}
				targetIconIterator.remove();
			}
		}
		@Override
		public void resolveParentExtensions(Object key, NodeModel to) {
        }
	}

	private final Map<MindIcon, AFreeplaneAction> iconActions = new LinkedHashMap<MindIcon, AFreeplaneAction>();
	private final IconStore STORE = IconStoreFactory.ICON_STORE;
	private final JToolBar iconToolBar;
	private final Box iconBox;

	/**
	 * @param modeController
	 */
	public MIconController(final ModeController modeController) {
		super(modeController);
		modeController.registerExtensionCopier(new ExtensionCopier());
		iconToolBar = new FreeplaneToolBar("icon_toolbar", SwingConstants.VERTICAL);
		JAutoScrollBarPane iconToolBarScrollPane = new JAutoScrollBarPane(iconToolBar);
		UITools.setScrollbarIncrement(iconToolBarScrollPane);
		UITools.addScrollbarIncrementPropertyListener(iconToolBarScrollPane);
		FrameController frameController = (FrameController) modeController.getController().getViewController();
		iconBox = new CollapseableBoxBuilder().setPropertyNameBase("leftToolbarVisible").setResizeable(true).createBox(iconToolBarScrollPane, Direction.LEFT);
		createIconActions(modeController);
		createPreferences();
		modeController.addUiBuilder(Phase.ACTIONS, "icon_actions", new IconActionBuilder(modeController));
	}

	@Override
	public void install(final ModeController modeController) {
		super.install(modeController);
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {

			@Override
			public void nodeChanged(NodeChangeEvent event) {
				final NodeModel node = event.getNode();
				if(event.getProperty().equals(NodeModel.NODE_ICON)
						&& LogicalStyleController.getController().conditionalStylesOf(node).dependOnCondition(DEPENDS_ON_ICON)){
					modeController.getMapController().delayedNodeRefresh(node, NodeModel.UNKNOWN_PROPERTY, null, null);
				}
			}
		});
	}


	public void addIcon(final NodeModel node, final MindIcon icon) {
		final IActor actor = new IActor() {
			@Override
			public void act() {
				node.addIcon(icon);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, null, icon);
			}

			@Override
			public String getDescription() {
				return "addIcon";
			}

			@Override
			public void undo() {
				node.removeIcon();
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, icon, null);
			}
		};
		Controller.getCurrentModeController().execute(actor, node.getMap());
	}

	public void addIcon(final NodeModel node, final MindIcon icon, final int position) {
		final IActor actor = new IActor() {
			@Override
			public void act() {
				node.addIcon(icon, position);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, null, icon);
			}

			@Override
			public String getDescription() {
				return "addIcon";
			}

			@Override
			public void undo() {
				node.removeIcon(position);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, icon, null);
			}
		};
		Controller.getCurrentModeController().execute(actor, node.getMap());
	}

	public void changeIconSize(final NodeModel node, final Quantity<LengthUnits> iconSize)
	{
		final IActor actor = new IActor() {

			private Quantity<LengthUnits> oldIconSize;

			@Override
			public void act() {
				oldIconSize = node.getSharedData().getIcons().getIconSize();
				node.getSharedData().getIcons().setIconSize(iconSize);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON_SIZE, null, iconSize);
			}

			@Override
			public String getDescription() {
				return "changeIconSize";
			}

			@Override
			public void undo() {
				node.getSharedData().getIcons().setIconSize(oldIconSize);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON_SIZE, oldIconSize, null);
			}
		};
		Controller.getCurrentModeController().execute(actor, node.getMap());
	}

	private void createIconActions(final ModeController modeController) {
		modeController.addAction(new RemoveIconAction(0));
		modeController.addAction(new RemoveIconAction(-1));
		modeController.addAction(new RemoveAllIconsAction());
		for (final MindIcon icon : STORE.getMindIcons()) {
			final IconAction myAction = new IconAction(icon);
			modeController.addActionIfNotAlreadySet(myAction);
			iconActions.put(icon, myAction);
		}
	}

	private void createPreferences() {
		final MModeController modeController = (MModeController) Controller.getCurrentModeController();
		final OptionPanelBuilder optionPanelBuilder = modeController.getOptionPanelBuilder();
		final List<AFreeplaneAction> actions = new ArrayList<AFreeplaneAction>();
		actions.addAll(iconActions.values());
		actions.add(modeController.getAction("RemoveIcon_0_Action"));
		actions.add(modeController.getAction("RemoveIconAction"));
		actions.add(modeController.getAction("RemoveAllIconsAction"));

		for (final AFreeplaneAction iconAction : actions) {
			final IIconInformation info = (IIconInformation) iconAction;
			optionPanelBuilder.addCreator("Keystrokes/icons", new IPropertyControlCreator() {
				@Override
				public IPropertyControl createControl() {
					final KeyProperty keyProperty = new KeyProperty(info.getShortcutKey(), info.getTranslationValueLabel());
					keyProperty.setIcon(info.getIcon());
					keyProperty.disableModifiers();
					return keyProperty;
				}
			}, IndexedTree.AS_CHILD);
		}
	}

	public Collection<AFreeplaneAction> getIconActions() {
		return Collections.unmodifiableCollection(iconActions.values());
	}

	/**
	 * @return
	 */
	public JComponent getIconToolBarScrollPane() {
		return iconBox;
	}

	public Collection<MindIcon> getMindIcons() {
		final List<MindIcon> iconInfoList = new ArrayList<MindIcon>();
		final Collection<AFreeplaneAction> iconActions = getIconActions();
		for (final Action action : iconActions) {
			final MindIcon info = ((IconAction) action).getMindIcon();
			iconInfoList.add(info);
		}
		return iconInfoList;
	}

	private JMenu getSubmenu( final IconGroup group) {
		final JMenu menu = new JMenu("\u25ba") {
			private static final long serialVersionUID = 1L;

			@Override
			protected Point getPopupMenuOrigin() {
				return new Point(getWidth(), 0);
			}
		};
		menu.setFont(ARROW_FONT);
		menu.setMargin(ICON_SUBMENU_INSETS);
		menu.setIcon(group.getGroupIcon().getIcon());
		for (final MindIcon icon : group.getIcons()) {
			addActionToIconSubmenu(menu, icon, icon.getFileName());
		}
		menu.setToolTipText(group.getDescription());
		return menu;
	}

	private void addActionToIconSubmenu(final JMenu menu, final MindIcon icon, final String fileName) {
		final AFreeplaneAction myAction = iconActions.get(icon);
		final int separatorPosition = fileName.indexOf('/');
		if (separatorPosition == -1) {
			new MenuSplitter().addMenuComponent(menu, new JMenuItem(myAction),  menu.getItemCount());
			return;
		}
		final String submenuName = fileName.substring(0, separatorPosition);
		final int componentCount = menu.getItemCount();
		if (componentCount != 0) {
			final Component lastComponent = menu.getMenuComponent(componentCount - 1);
			if (lastComponent instanceof JMenu) {
				final JMenu lastSubmenu = (JMenu) lastComponent;
				if (lastSubmenu.getText().equals(submenuName)) {
					addActionToIconSubmenu(lastSubmenu, icon, fileName.substring(separatorPosition + 1));
					return;
				}
			}
		}
		final JMenu submenu = new JMenu(submenuName);
		menu.add(submenu);
		addActionToIconSubmenu(submenu, icon, fileName.substring(separatorPosition + 1));
	}

	private void insertSubmenus(final JToolBar iconToolBar) {
		final JMenuBar iconMenuBar = new JMenuBar() {
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getMaximumSize() {
				final Dimension preferredSize = getPreferredSize();
				return new Dimension(Short.MAX_VALUE, preferredSize.height);
			}
		};
		iconMenuBar.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		iconMenuBar.setLayout(new GridLayout(0, 1));
		for (final IconGroup iconGroup : STORE.getGroups()) {
			iconMenuBar.add(getSubmenu(iconGroup));
		}
		iconToolBar.add(iconMenuBar);
	}

	public void removeAllIcons(final NodeModel node) {
		final int size = node.getIcons().size();
		final MIconController iconController = (MIconController) IconController.getController();
		for (int i = 0; i < size; i++) {
			iconController.removeIcon(node, 0);
		}
	}

	public int removeIcon(final NodeModel node) {
		return removeIcon(node, -1);
	}

	public int removeIcon(final NodeModel node, final int position) {
		final int size = node.getIcons().size();
		final int index = position >= 0 ? position : size + position;
		if (size == 0 || size <= index) {
			return size;
		}
		final IActor actor = new IActor() {
			private final MindIcon icon = node.getIcon(index);

			@Override
			public void act() {
				node.removeIcon(index);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, icon, null);
			}

			@Override
			public String getDescription() {
				return "removeIcon";
			}

			@Override
			public void undo() {
				node.addIcon(icon, index);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, NodeModel.NODE_ICON, null, icon);
			}
		};
		Controller.getCurrentModeController().execute(actor, node.getMap());
		return node.getIcons().size();
	}

	private void updateIconToolbar(ModeController modeController) {
		iconToolBar.removeAll();
		iconToolBar.add(modeController.getAction("RemoveIcon_0_Action"))
		    .setAlignmentX(JComponent.CENTER_ALIGNMENT);
		iconToolBar.add(modeController.getAction("RemoveIconAction")).setAlignmentX(JComponent.CENTER_ALIGNMENT);
		iconToolBar.add(modeController.getAction("RemoveAllIconsAction")).setAlignmentX(
		    JComponent.CENTER_ALIGNMENT);
		iconToolBar.addSeparator();
		if (ResourceController.getResourceController().getBooleanProperty("structured_icon_toolbar")) {
			insertSubmenus(iconToolBar);
			return;
		}
		final String[] fpIcons = ResourceController.getResourceController().getProperty("icons.list").split(";");
		for (final String icon : fpIcons) {
			final MindIcon mindIcon = STORE.getMindIcon(icon);
			final AFreeplaneAction iconAction = iconActions.get(mindIcon);
			iconToolBar.add(iconAction).setAlignmentX(JComponent.CENTER_ALIGNMENT);
		}
		final Collection<MindIcon> userIcons = STORE.getUserIcons();
		for (final MindIcon icon : userIcons) {
			final AFreeplaneAction iconAction = iconActions.get(icon);
			iconToolBar.add(iconAction).setAlignmentX(JComponent.CENTER_ALIGNMENT);
		}
	}

	/** lists all icons that are available in the icon selection dialog. This may include user icons
	 * if there are some installed. */
	public static List<String> listStandardIconKeys() {
		// the source of this list is the property "icons.list" in freeplane.properties
		ArrayList<String> result = new ArrayList<String>();
		final MIconController mIconController = (MIconController) IconController.getController();
		for (MindIcon mindIcon : mIconController.getMindIcons())
			result.add(mindIcon.getName());
		return result;
	}

}
