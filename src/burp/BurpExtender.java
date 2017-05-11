package burp;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

public class BurpExtender implements IBurpExtender, ITab, ISessionHandlingAction
{
	private IExtensionHelpers helpers;
	private final static String NAME = "Uniqueness";
	private final static String[] helpText = {
		"<html><body>By clicking on <b>Compile</b> above, the regular expression will be compiled.</body></html>",
		"If no error messages are shown, it was successful, and it's ready",
		"for use through the " + NAME + "session handling action."
	};
	private Pattern regexp = null;
	private final static AtomicInteger counter = new AtomicInteger();

	// IBurpExtender

	@Override
	public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
	{
		helpers = callbacks.getHelpers();
		createTab();

		callbacks.setExtensionName(NAME);
		callbacks.addSuiteTab(this);
		callbacks.registerSessionHandlingAction(this);
	}

	// ITab

	@Override public String getTabCaption() { return NAME; }
	@Override public Component getUiComponent() { return uiComponent; }

	// ISessionHandlingAction

	@Override public String getActionName() { return NAME; }

	@Override
	public void performAction(final IHttpRequestResponse currentRequest,
			final IHttpRequestResponse[] macroItems) {
		if (regexp == null) return;
		String req = helpers.bytesToString(currentRequest.getRequest());
		Matcher m = regexp.matcher(req);
		if (!m.find()) return;
		req = req.substring(0, m.start(1)) + counter.getAndIncrement() + req.substring(m.end(1));
		currentRequest.setRequest(helpers.stringToBytes(req));
	}

	// End of interfaces

	private enum RegExpFlag {
		CASE_INSENSITIVE, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ,
		UNIX_LINES, LITERAL, UNICODE_CHARACTER_CLASS, COMMENTS;

		public final int value;
		
		RegExpFlag() {
			int v;
			try {
				v = Pattern.class.getField(super.toString()).getInt(null);
			} catch (Exception e) {
				v = 0;
			}
			value = v;
		}

		@Override
		public String toString() {
			 return super.toString().toLowerCase().replace("_", " ");
		}
	}

	private Component uiComponent;

	private void createTab() {
		final Map<RegExpFlag, JCheckBox> cbFlags = new EnumMap<RegExpFlag, JCheckBox>(RegExpFlag.class);
		JPanel panel = new JPanel(new GridBagLayout());
		uiComponent = panel;
		GridBagConstraints cs = new GridBagConstraints();
		cs.fill = GridBagConstraints.HORIZONTAL;

		cs.gridx = 0; cs.gridy = 0; cs.gridwidth = 1;
		panel.add(new JLabel("Regular expression: "), cs);

		cs.gridx = 1;
		final JTextField tfRegExp = new JTextField();
		panel.add(tfRegExp, cs);

		cs.gridx = 2;
		final JButton btnCompile = new JButton("Compile");
		btnCompile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final Pattern p = handleRegexpCompilation(tfRegExp.getText(), checkBoxMapToFlags(cbFlags));
				if (p != null) regexp = p;
			}
		});

		panel.add(btnCompile, cs);

		cs.gridx = 0; cs.gridy = 1; cs.gridwidth = 2;
		panel.add(new JLabel("Regular expression flags: (see JDK documentation)"), cs);

		cs.gridy = 2; cs.gridwidth = 1;
		for (RegExpFlag flag : RegExpFlag.values()) {
			JCheckBox cb = new JCheckBox(flag.toString());
			panel.add(cb, cs);
			cbFlags.put(flag, cb);
			if (cs.gridx == 0) {
				cs.gridx = 1;
			} else {
				cs.gridy++;
				cs.gridx = 0;
			}
		}

		cs.gridx = 0; cs.gridwidth = 2;
		for (String line : helpText) {
			cs.gridy++;
			panel.add(new JLabel(line), cs);
		}
	}

	private static int checkBoxMapToFlags(Map<RegExpFlag, JCheckBox> cbFlags) {
		int flags = 0;
		for (Map.Entry<RegExpFlag, JCheckBox> e : cbFlags.entrySet()) {
			if (e.getValue().isSelected()) flags |= e.getKey().value;
		}
		return flags;
	}

	private Pattern handleRegexpCompilation(String regexp, int flags) {
		if (regexp.indexOf('(') == -1 || regexp.indexOf(')') == -1) {
			JOptionPane.showMessageDialog(uiComponent, "No group was found in the regular expression. " +
					"There must be at least one group that can be used to find what to replace.",
					"Missing group in regular expression", JOptionPane.ERROR_MESSAGE);
			return null;
		}
		try {
			return Pattern.compile(regexp, flags);
		} catch (PatternSyntaxException pse) {
			JOptionPane.showMessageDialog(uiComponent, pse.getMessage(),
					"Syntax error in regular expression", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
}
