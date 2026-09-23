package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.List;

/** Native AlertDialog has a LIGHT surface, independent of the EDHOME screen skin.
 * Keep both the selected spinner row and its dropdown on the same light palette.
 */
final class DialogContrast {
    static final int BACKGROUND = Color.WHITE;
    static final int TEXT = 0xFF202124;
    static final int HINT = 0xFF667085;
    static final int LABEL = 0xFF475467;
    static final int LINE = 0xFF98A2B3;

    private DialogContrast() { }

    static void apply(View root, int accent) {
        if (root instanceof Spinner) return; // Styled by spinnerAdapter.
        if (root instanceof EditText) {
            EditText input = (EditText) root;
            input.setTextColor(TEXT);
            input.setHintTextColor(HINT);
            input.setHighlightColor(0x553A9D90);
            input.setBackgroundTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {accent, LINE}));
            return;
        }
        if (root instanceof TextView) {
            ((TextView) root).setTextColor(LABEL);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                apply(group.getChildAt(i), accent);
        }
    }

    static ArrayAdapter<String> spinnerAdapter(Context context, List<String> values) {
        return new ArrayAdapter<String>(
                context, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View recycled,
                    ViewGroup parent) {
                View view = super.getView(position, recycled, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(TEXT);
                    view.setPadding(dp(context, 9), dp(context, 12),
                        dp(context, 9), dp(context, 12));
                }
                return view;
            }

            @Override public View getDropDownView(int position, View recycled,
                    ViewGroup parent) {
                View view = super.getDropDownView(position, recycled, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(TEXT);
                    view.setBackgroundColor(BACKGROUND);
                    view.setPadding(dp(context, 10), dp(context, 14),
                        dp(context, 10), dp(context, 14));
                }
                return view;
            }
        };
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources()
            .getDisplayMetrics().density + 0.5f);
    }
}
