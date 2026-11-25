using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Automation.Text;
using System.Windows.Controls;

namespace NoteSync;

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        if (args.Contains("--self-test"))
        {
            if (ListMap.Parse("1. A\n1. B\n1. C", "Auto")[2] != 3 ||
                ListMap.Parse("1. A\n5. B", "Literal")[1] != 5 ||
                ListMap.Parse("4. A\n9. B", "Markdown")[1] != 5 ||
                ListMap.Parse("title\r\n1. A\r\n2. B", "Auto")[2] != 2)
                Environment.Exit(1);
            return;
        }
        new Application().Run(new MainWindow());
    }
}

internal static class ListMap
{
    static readonly Regex Marker = new(@"^\s*(\d{1,9})[.)]\s+", RegexOptions.Compiled);
    public static string[] Lines(string text) =>
        text.Replace("\r\n", "\n").Replace('\r', '\n').Split('\n');
    public static int LineOf(string text) => Lines(text).Length - 1;
    public static int?[] Parse(string text, string mode)
    {
        var lines = Lines(text);
        var result = new int?[lines.Length];
        for (int i = 0; i < lines.Length;)
        {
            var m = Marker.Match(lines[i]);
            if (!m.Success) { i++; continue; }
            var start = i;
            var numbers = new List<int>();
            while (i < lines.Length && (m = Marker.Match(lines[i])).Success)
            {
                numbers.Add(int.Parse(m.Groups[1].Value));
                i++;
            }
            bool sequential = mode == "Markdown" ||
                (mode == "Auto" && numbers.Count > 1 && numbers.Distinct().Count() < numbers.Count);
            for (int j = 0; j < numbers.Count; j++)
                result[start + j] = sequential ? numbers[0] + j : numbers[j];
        }
        return result;
    }
}

internal sealed class MainWindow : Window
{
    readonly TextBox titleFilter = new() { Margin = new Thickness(0, 5, 0, 12) };
    readonly ComboBox mode = new() { Margin = new Thickness(0, 5, 0, 12), ItemsSource = new[] { "Auto", "Literal", "Markdown" }, SelectedIndex = 0 };
    readonly TextBlock status = new() { Text = "Остановлено", TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 12, 0, 0) };
    readonly Button toggle = new() { Content = "Запустить", Height = 36 };
    readonly CheckBox scrollOnly = new() { Content = "Только прокрутка (без выделения)", Margin = new Thickness(0, 0, 0, 12) };
    CancellationTokenSource? session;
    public MainWindow()
    {
        Title = "NoteSync";
        Width = 470; Height = 340; MinWidth = 430; MinHeight = 340;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        var panel = new StackPanel { Margin = new Thickness(22) };
        panel.Children.Add(new TextBlock { Text = "Синхронизация двух заметок", FontSize = 21, FontWeight = FontWeights.SemiBold });
        panel.Children.Add(new TextBlock { Text = "Точный заголовок обоих окон (пусто — авто)", Margin = new Thickness(0, 14, 0, 0) });
        panel.Children.Add(titleFilter);
        panel.Children.Add(new TextBlock { Text = "Нумерация: Auto / Literal / Markdown" });
        panel.Children.Add(mode);
        panel.Children.Add(scrollOnly);
        panel.Children.Add(toggle);
        panel.Children.Add(status);
        Content = panel;
        toggle.Click += (_, _) => Toggle();
        Closed += (_, _) => session?.Cancel();
    }
    void Toggle()
    {
        if (session != null)
        {
            session.Cancel(); session = null;
            toggle.Content = "Запустить"; status.Text = "Остановлено";
            titleFilter.IsEnabled = mode.IsEnabled = scrollOnly.IsEnabled = true;
            return;
        }
        var current = new CancellationTokenSource();
        session = current;
        var filter = titleFilter.Text.Trim();
        var selectedMode = (string)mode.SelectedItem;
        bool onlyScroll = scrollOnly.IsChecked == true;
        toggle.Content = "Остановить";
        titleFilter.IsEnabled = mode.IsEnabled = scrollOnly.IsEnabled = false;
        status.Text = "Открой две заметки с одинаковым заголовком и поставь курсор в одну из них.";
        var thread = new Thread(() =>
        {
            try
            {
                new Synchronizer(filter, selectedMode, onlyScroll, message =>
                {
                    if (!current.IsCancellationRequested)
                        Dispatcher.BeginInvoke(new Action(() =>
                        {
                            if (session == current) status.Text = message;
                        }));
                }).Run(current.Token);
            }
            finally { current.Dispose(); }
        }) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.MTA);
        thread.Start();
        WindowState = WindowState.Minimized;
    }
}

internal sealed class Synchronizer(string filter, string mode, bool scrollOnly, Action<string> report)
{
    readonly Dictionary<nint, AutomationElement> editors = new();
    (nint from, nint to, int line, int? number, string text)? last;
    string previousMessage = "";
    void Status(string message)
    {
        if (message == previousMessage) return;
        previousMessage = message; report(message);
    }
    public void Run(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try { Tick(token); }
            catch (Exception e) when (e is not OutOfMemoryException)
            {
                last = null; editors.Clear();
                Status("UI Automation: " + e.Message);
            }
            token.WaitHandle.WaitOne(180);
        }
    }
    void Tick(CancellationToken token)
    {
        var source = Native.GetForegroundWindow();
        if (source == 0 || Native.Own(source)) return;
        var title = Native.Title(source);
        if (title.Length == 0 || (filter.Length > 0 && !string.Equals(title, filter, StringComparison.OrdinalIgnoreCase))) return;
        var targets = Native.Windows().Where(w => w != source && !Native.Own(w) &&
            string.Equals(Native.Title(w), title, StringComparison.OrdinalIgnoreCase)).ToArray();
        if (targets.Length != 1)
        {
            last = null;
            Status(targets.Length == 0 ? "Второе окно с таким заголовком не найдено." : "Найдено больше двух окон; закрой лишние.");
            return;
        }
        var target = targets[0];
        if (Native.IsIconic(target)) { Status("Разверни вторую заметку."); last = null; return; }
        var root = AutomationElement.FromHandle(source);
        var focus = AutomationElement.FocusedElement;
        var sourceEditor = FindFocused(focus, root);
        if (sourceEditor == null)
        {
            Status("Поставь курсор в текст заметки (нужен UIA TextPattern)."); last = null; return;
        }
        editors[source] = sourceEditor;
        if (!TryText(sourceEditor, out var src) || src == null) return;
        var selections = src.GetSelection();
        if (selections.Length != 1) { last = null; Status("Редактор не отдаёт одиночную позицию курсора."); return; }
        var caret = selections[0].Clone();
        caret.MoveEndpointByRange(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start);
        var prefix = src.DocumentRange.Clone();
        prefix.MoveEndpointByRange(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start);
        string before = prefix.GetText(-1);
        string sourceText = src.DocumentRange.GetText(-1);
        int line = ListMap.LineOf(before);
        var numbers = ListMap.Parse(sourceText, mode);
        if (line >= numbers.Length) return;
        int? number = numbers[line];
        if (number == null) number = ItemNumber(sourceEditor, src, caret);
        var destEditor = TargetEditor(target);
        if (destEditor == null || !TryText(destEditor, out var dst) || dst == null)
        {
            last = null; Status("Во втором окне нет доступного текстового редактора."); return;
        }
        string destText = dst.DocumentRange.GetText(-1);
        var key = (source, target, line, number, destText);
        if (last == key) return;
        TextPatternRange? range = null;
        if (number.HasValue)
        {
            var matches = ListMap.Parse(destText, mode).Select((n, i) => (n, i))
                .Where(x => x.n == number).ToArray();
            if (matches.Length == 1) range = FindLine(dst, destText, matches[0].i);
            else if (matches.Length == 0)
            {
                var items = Items(destEditor, dst);
                var match = items.Where(x => x.number == number).ToArray();
                if (match.Length == 1) range = match[0].range;
            }
        }
        else range = FindLine(dst, destText, line);
        if (range == null)
        {
            last = null; Status("Соответствующий пункт не найден однозначно."); return;
        }
        if (token.IsCancellationRequested || Native.GetForegroundWindow() != source) return;
        // Never activate either window or simulate keyboard input.
        range.ScrollIntoView(false);
        if (!scrollOnly && dst.SupportedTextSelection != SupportedTextSelection.None)
        {
            if (Native.GetForegroundWindow() != source) { Status("Редактор перехватил фокус при прокрутке; останови синхронизацию."); return; }
            range.Select();
        }
        if (Native.GetForegroundWindow() != source)
        {
            Status("Редактор изменил фокус; синхронизация этой пары приостановлена. Используй режим только прокрутки.");
            // Stop this session rather than repeatedly stealing focus.
            token.WaitHandle.WaitOne();
            return;
        }
        last = key;
        Status((number.HasValue ? $"Пункт {number}" : $"Строка {line + 1}") +
            (scrollOnly || dst.SupportedTextSelection == SupportedTextSelection.None ? " — прокручено" : " — выделено"));
    }
    static bool TryText(AutomationElement element, out TextPattern? text)
    {
        text = null;
        if (!element.TryGetCurrentPattern(TextPattern.Pattern, out var value)) return false;
        text = value as TextPattern;
        return text != null;
    }
    static AutomationElement? FindFocused(AutomationElement? current, AutomationElement root)
    {
        AutomationElement? candidate = null;
        for (int i = 0; current != null && i < 40; i++)
        {
            if (current.Equals(root)) return candidate;
            if (candidate == null && TryText(current, out _)) candidate = current;
            current = TreeWalker.RawViewWalker.GetParent(current);
        }
        return null;
    }
    AutomationElement? TargetEditor(nint hwnd)
    {
        if (editors.TryGetValue(hwnd, out var cached))
        {
            try { if (TryText(cached, out _)) return cached; } catch (ElementNotAvailableException) { }
            editors.Remove(hwnd);
        }
        var root = AutomationElement.FromHandle(hwnd);
        var condition = new OrCondition(new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Document),
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Edit));
        var candidates = root.FindAll(TreeScope.Descendants, condition).Cast<AutomationElement>()
            .Where(e => !e.Current.IsOffscreen && TryText(e, out _))
            .OrderByDescending(e => e.Current.BoundingRectangle.Width * e.Current.BoundingRectangle.Height).ToArray();
        var found = candidates.FirstOrDefault();
        if (found != null) editors[hwnd] = found;
        return found;
    }
    static TextPatternRange? FindLine(TextPattern pattern, string doc, int line)
    {
        var lines = ListMap.Lines(doc);
        if (line < 0 || line >= lines.Length) return null;
        var value = lines[line];
        if (value.Length == 0) return null;
        var search = pattern.DocumentRange.Clone();
        for (int i = 0; i < 20000; i++)
        {
            var match = search.FindText(value, false, false);
            if (match == null) return null;
            var prefix = pattern.DocumentRange.Clone();
            prefix.MoveEndpointByRange(TextPatternRangeEndpoint.End, match, TextPatternRangeEndpoint.Start);
            var before = prefix.GetText(-1);
            int foundLine = ListMap.LineOf(before);
            if (foundLine == line && (before.Length == 0 || before.EndsWith("\n") || before.EndsWith("\r"))) return match;
            if (foundLine > line) return null;
            search.MoveEndpointByRange(TextPatternRangeEndpoint.Start, match, TextPatternRangeEndpoint.End);
        }
        return null;
    }
    static List<(int number, TextPatternRange range)> Items(AutomationElement editor, TextPattern pattern)
    {
        var result = new List<(int, TextPatternRange)>();
        var items = editor.FindAll(TreeScope.Descendants,
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.ListItem));
        int i = 0;
        foreach (AutomationElement item in items)
        {
            i++;
            try
            {
                var r = pattern.RangeFromChild(item);
                if (r != null)
                {
                    var match = Regex.Match(r.GetText(-1).TrimStart(), @"^(\d{1,9})[.)]\s");
                    result.Add((match.Success ? int.Parse(match.Groups[1].Value) : i, r));
                }
            }
            catch (ArgumentException) { }
            catch (InvalidOperationException) { }
        }
        return result;
    }
    static int? ItemNumber(AutomationElement editor, TextPattern pattern, TextPatternRange caret)
    {
        foreach (var (number, range) in Items(editor, pattern))
        {
            if (range.CompareEndpoints(TextPatternRangeEndpoint.Start, caret, TextPatternRangeEndpoint.Start) <= 0 &&
                range.CompareEndpoints(TextPatternRangeEndpoint.End, caret, TextPatternRangeEndpoint.Start) > 0)
                return number;
        }
        return null;
    }
}

internal static class Native
{
    delegate bool EnumProc(nint hwnd, nint param);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, nint param);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(nint hwnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(nint hwnd, System.Text.StringBuilder text, int max);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(nint hwnd, out uint pid);
    [DllImport("user32.dll")] public static extern nint GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool IsIconic(nint hwnd);
    public static bool Own(nint hwnd)
    {
        GetWindowThreadProcessId(hwnd, out uint pid);
        return pid == Environment.ProcessId;
    }
    public static string Title(nint hwnd)
    {
        var text = new System.Text.StringBuilder(2048);
        GetWindowText(hwnd, text, text.Capacity);
        return text.ToString().Trim();
    }
    public static List<nint> Windows()
    {
        var windows = new List<nint>();
        EnumWindows((hwnd, _) => { if (IsWindowVisible(hwnd)) windows.Add(hwnd); return true; }, 0);
        return windows;
    }
}
