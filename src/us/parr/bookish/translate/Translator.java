package us.parr.bookish.translate;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.Triple;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.xpath.XPath;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.StringRenderer;
import us.parr.bookish.Tool;
import us.parr.bookish.model.Abstract;
import us.parr.bookish.model.Aside;
import us.parr.bookish.model.Author;
import us.parr.bookish.model.BlockCode;
import us.parr.bookish.model.BlockEquation;
import us.parr.bookish.model.BlockImage;
import us.parr.bookish.model.Bold;
import us.parr.bookish.model.Book;
import us.parr.bookish.model.Callout;
import us.parr.bookish.model.ChapQuote;
import us.parr.bookish.model.Chapter;
import us.parr.bookish.model.Citation;
import us.parr.bookish.model.ContainerWithTitle;
import us.parr.bookish.model.Document;
import us.parr.bookish.model.EqnIndexedVar;
import us.parr.bookish.model.EqnIndexedVecVar;
import us.parr.bookish.model.EqnVar;
import us.parr.bookish.model.EqnVecVar;
import us.parr.bookish.model.HyperLink;
import us.parr.bookish.model.Image;
import us.parr.bookish.model.InlineCode;
import us.parr.bookish.model.InlineEquation;
import us.parr.bookish.model.InlinePyEval;
import us.parr.bookish.model.Italics;
import us.parr.bookish.model.Join;
import us.parr.bookish.model.Latex;
import us.parr.bookish.model.LineBreak;
import us.parr.bookish.model.ListItem;
import us.parr.bookish.model.OrderedList;
import us.parr.bookish.model.Other;
import us.parr.bookish.model.OutputModelObject;
import us.parr.bookish.model.Paragraph;
import us.parr.bookish.model.PreAbstract;
import us.parr.bookish.model.PyEval;
import us.parr.bookish.model.PyEvalDataFrame;
import us.parr.bookish.model.PyFig;
import us.parr.bookish.model.Quoted;
import us.parr.bookish.model.Section;
import us.parr.bookish.model.SideFigure;
import us.parr.bookish.model.SideNote;
import us.parr.bookish.model.SideQuote;
import us.parr.bookish.model.Site;
import us.parr.bookish.model.SubSection;
import us.parr.bookish.model.SubSubSection;
import us.parr.bookish.model.TODO;
import us.parr.bookish.model.Table;
import us.parr.bookish.model.TableHeaderItem;
import us.parr.bookish.model.TableItem;
import us.parr.bookish.model.TableRow;
import us.parr.bookish.model.TextBlock;
import us.parr.bookish.model.UnOrderedList;
import us.parr.bookish.model.XMLEndTag;
import us.parr.bookish.model.XMLTag;
import us.parr.bookish.model.entity.ChapterDef;
import us.parr.bookish.model.entity.CitationDef;
import us.parr.bookish.model.entity.EntityDef;
import us.parr.bookish.model.entity.FigureDef;
import us.parr.bookish.model.entity.SectionDef;
import us.parr.bookish.model.entity.SideFigDef;
import us.parr.bookish.model.entity.SideNoteDef;
import us.parr.bookish.model.entity.SideQuoteDef;
import us.parr.bookish.model.entity.SiteDef;
import us.parr.bookish.model.entity.SubSectionDef;
import us.parr.bookish.model.entity.SubSubSectionDef;
import us.parr.bookish.model.ref.ChapterRef;
import us.parr.bookish.model.ref.CitationRef;
import us.parr.bookish.model.ref.EntityRef;
import us.parr.bookish.model.ref.FigureRef;
import us.parr.bookish.model.ref.SectionRef;
import us.parr.bookish.model.ref.SideNoteRef;
import us.parr.bookish.model.ref.SiteRef;
import us.parr.bookish.model.ref.UnknownRef;
import us.parr.bookish.parse.BookishParser;
import us.parr.bookish.parse.BookishParserBaseVisitor;
import us.parr.bookish.parse.XMLLexer;
import us.parr.bookish.parse.XMLParser;
import us.parr.bookish.util.DataTable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static us.parr.bookish.parse.BookishParser.END_TAG;
import static us.parr.bookish.translate.Tex2SVG.LatexType.BLOCKEQN;
import static us.parr.bookish.translate.Tex2SVG.LatexType.LATEX;
import static us.parr.lib.ParrtCollections.join;
import static us.parr.lib.ParrtStrings.md5hash;
import static us.parr.lib.ParrtStrings.stripQuotes;

public class Translator extends BookishParserBaseVisitor<OutputModelObject> {
	public static int INLINE_EQN_FONT_SIZE = 13;
	public static int BLOCK_EQN_FONT_SIZE = 13;
	public static Map<Class<? extends EntityDef>,Class<? extends EntityRef>> defToRefMap =
		new HashMap<Class<? extends EntityDef>,Class<? extends EntityRef>>() {{
			put(CitationDef.class, CitationRef.class);
			put(FigureDef.class, FigureRef.class);
			put(SideFigDef.class, FigureRef.class);
			put(SideNoteDef.class, SideNoteRef.class);
			put(SideQuoteDef.class, SideNoteRef.class);
			put(SiteDef.class, SiteRef.class);
			put(SectionDef.class, SectionRef.class);
			put(SubSectionDef.class, SectionRef.class);
			put(SubSubSectionDef.class, SectionRef.class);
			put(ChapterDef.class, ChapterRef.class);
		}};

	public STGroupFile templates;

	public static Pattern eqnVarPattern;
	public static Pattern eqnVecVarPattern, eqnVecVarPattern2;
	public static Pattern eqnIndexedVarPattern;
	public static Pattern eqnIndexedVecVarPattern, eqnIndexedVecVarPattern2;
	public static Pattern sectionAnchorPattern;
	public static Pattern latexPattern;

	public String outputDir;
	public Tool.Target target;

	public Book book;
	public Map<String, EntityDef> entities;
	public Document document;
	public Tex2SVG texConverter;

	public Translator(Book book, Map<String, EntityDef> entities, Tool.Target target, String outputDir) {
		this.book = book;
		this.target = target;
		this.entities = entities;
		String templateFileName = null;
		switch ( target ) {
			case HTML :
				templateFileName = "templates/HTML.stg";
				templates = new STGroupFile(templateFileName);
				templates.registerRenderer(String.class, new StringRenderer());
				break;
			case LATEX :
				templateFileName = "templates/latex.stg";
				templates = new STGroupFile(templateFileName);
				templates.registerRenderer(String.class, new LatexEscaper());
				break;
			case LATEX_BOOK :
				templateFileName = "templates/latex-book.stg";
				templates = new STGroupFile(templateFileName);
				templates.registerRenderer(String.class, new LatexEscaper());
				break;
		}
		this.outputDir = outputDir;

		texConverter = new Tex2SVG(outputDir);

		eqnVarPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*)");
		eqnIndexedVarPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*)_([a-zA-Z][a-zA-Z0-9]*)");
		eqnVecVarPattern = Pattern.compile("\\\\mathbf\\{([a-zA-Z][a-zA-Z0-9]*)\\}");
		eqnVecVarPattern2 = Pattern.compile("\\\\vec\\{([a-zA-Z][a-zA-Z0-9]*)\\}");
		eqnIndexedVecVarPattern = Pattern.compile("\\\\mathbf\\{([a-zA-Z][a-zA-Z0-9]*)\\}_([a-zA-Z][a-zA-Z0-9]*)");
		eqnIndexedVecVarPattern2 = Pattern.compile("\\\\vec\\{([a-zA-Z][a-zA-Z0-9]*)\\}_([a-zA-Z][a-zA-Z0-9]*)");
		sectionAnchorPattern = Pattern.compile(".*\\(([a-zA-Z_][a-zA-Z0-9\\-_:]*?)\\)");
		latexPattern = Pattern.compile("\\\\latex\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
	}

	@Override
	protected OutputModelObject aggregateResult(OutputModelObject aggregate, OutputModelObject nextResult) {
		if ( aggregate == null ) {
			return nextResult;
		}
		if ( nextResult == null ) {
			return aggregate;
		}
		if ( aggregate instanceof Join ) {
			List<OutputModelObject> elements = new ArrayList<>();
			elements.addAll(((Join) aggregate).elements);
			elements.add(nextResult);
			return new Join(elements);
		}
		return new Join(aggregate, nextResult);
	}

	@Override
	public OutputModelObject visitDocument(BookishParser.DocumentContext ctx) {
		this.document = new Document(ctx);
		document.book = book;
		document.entities = entities;
		document.chapter = (Chapter)visit(ctx.chapter());
		return document;
	}

	@Override
	public OutputModelObject visitChapter(BookishParser.ChapterContext ctx) {
		String title = ctx.chap.getText();
		title = title.substring(title.indexOf(' ')+1).trim();

		Pair<String, String> results = splitSectionTitle(title);
		title = results.a;
		String anchor = results.b;
		EntityDef def = null;
		if ( anchor!=null ) {
			def = document.getEntity(anchor);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), anchor);
				return null;
			}
		}

		OutputModelObject auth = null;
		if ( ctx.author()!=null ) {
			auth = visit(ctx.author());
		}
		OutputModelObject preabs = null;
		if ( ctx.preabstract()!=null ) {
			preabs = visit(ctx.preabstract());
		}
		OutputModelObject abs = null;
		if ( ctx.abstract_()!=null ) {
			abs = visit(ctx.abstract_());
		}
		List<ContainerWithTitle> sections = new ArrayList<>();
		for (ParseTree el : ctx.children) {
			if ( el instanceof BookishParser.SectionContext ) {
				OutputModelObject m = visit(el);
				sections.add((Section)m);
			}
		}
		Join sec = (Join)visitSection_content(ctx.section_content());
		Chapter chapter = new Chapter(def,
		                              title, null,
		                              (Author)auth, (PreAbstract)preabs,
		                              (Abstract)abs, sec!=null?sec.elements:null, sections);
		return chapter;
	}

	@Override
	public OutputModelObject visitAuthor(BookishParser.AuthorContext ctx) {
		return new Author(visit(ctx.paragraph_optional_blank_line()));
	}

	@Override
	public OutputModelObject visitPreabstract(BookishParser.PreabstractContext ctx) {
		List<OutputModelObject> paras = new ArrayList<>();
		paras.add(visit(ctx.paragraph_optional_blank_line()));
		for (ParseTree p : ctx.paragraph()) {
			Paragraph para = (Paragraph) visit(p);
			paras.add(para);
		}
		return new PreAbstract(paras);
	}

	@Override
	public OutputModelObject visitAbstract_(BookishParser.Abstract_Context ctx) {
		List<OutputModelObject> paras = new ArrayList<>();
		paras.add(visit(ctx.paragraph_optional_blank_line()));
		for (ParseTree p : ctx.paragraph()) {
			Paragraph para = (Paragraph) visit(p);
			paras.add(para);
		}
		return new Abstract(paras);
	}

	@Override
	public OutputModelObject visitSection(BookishParser.SectionContext ctx) {
		String title = ctx.sec.getText();
		title = title.substring(title.indexOf(' ')+1).trim();

		Pair<String, String> results = splitSectionTitle(title);
		title = results.a;
		String anchor = results.b;

		EntityDef def = null;
		if ( anchor!=null ) {
			def = document.getEntity(anchor);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), anchor);
				return null;
			}
		}

		List<ContainerWithTitle> subsections = new ArrayList<>();
		for (ParseTree el : ctx.subsection()) {
			subsections.add((SubSection)visit(el));
		}
		Join sec = (Join)visitSection_content(ctx.section_content());
		return new Section(def, title, anchor, sec.elements, subsections);
	}

	@Override
	public OutputModelObject visitSubsection(BookishParser.SubsectionContext ctx) {
		String title = ctx.sec.getText();
		title = title.substring(title.indexOf(' ')+1).trim();

		Pair<String, String> results = splitSectionTitle(title);
		title = results.a;
		String anchor = results.b;

		EntityDef def = null;
		if ( anchor!=null ) {
			def = document.getEntity(anchor);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), anchor);
				return null;
			}
		}

		List<ContainerWithTitle> subsubsections = new ArrayList<>();
		for (ParseTree el : ctx.subsubsection()) {
			subsubsections.add((SubSection)visit(el));
		}
		Join sec = (Join)visitSection_content(ctx.section_content());
		return new SubSection(def, title, anchor, sec!=null?sec.elements:null, subsubsections);
	}

	@Override
	public OutputModelObject visitSubsubsection(BookishParser.SubsubsectionContext ctx) {
		String title = ctx.sec.getText();
		title = title.substring(title.indexOf(' ')+1).trim();

		Pair<String, String> results = splitSectionTitle(title);
		title = results.a;
		String anchor = results.b;

		EntityDef def = null;
		if ( anchor!=null ) {
			def = document.getEntity(anchor);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), anchor);
			}
		}

		Join sec = (Join)visitSection_content(ctx.section_content());
		return new SubSubSection(def, title, anchor, sec!=null?sec.elements:null);
	}

	@Override
	public OutputModelObject visitSection_content(BookishParser.Section_contentContext ctx) {
		if ( ctx.children==null ) {
			return null;
		}

		List<OutputModelObject> elements = new ArrayList<>();
		for (ParseTree el : ctx.children) {
			OutputModelObject m = visit(el);
			elements.add(m);
		}
		return new Join(elements);
	}

	@Override
	public OutputModelObject visitParagraph_content(BookishParser.Paragraph_contentContext ctx) {
		List<OutputModelObject> elements = new ArrayList<>();
		for (ParseTree el : ctx.children) {
			OutputModelObject c = visit(el);
			if ( c!=null ) {
				elements.add(c);
			}
		}
		// find all REFs within paragraph
		Collection<ParseTree> refNodes =
			XPath.findAll(ctx, "//REF", new BookishParser(null));
		List<EntityDef> entitiesRefd = new ArrayList<>();
		for (ParseTree t : refNodes) {
			String label = stripQuotes(t.getText());
			EntityDef def = document.getEntity(label);
			if ( def!=null ) {
				if ( !book.entitiesRendered.contains(def) &&
					 !document.entitiesRendered.contains(def) )
				{
					entitiesRefd.add(def); // Nobody has shown it yet
					if ( document.entitiesRendered.contains(def) ) {
						document.entitiesRendered.add(def);
					}
					if ( book.entitiesRendered.contains(def) ) {
						book.entitiesRendered.add(def);
					}
				}
			}
			else {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
			}
		}
		return new Paragraph(elements, entitiesRefd);
	}

	@Override
	public OutputModelObject visitOther(BookishParser.OtherContext ctx) {
		String text = ctx.getText();
		if ( text.equals("\\$") ) text = "$";
		return new Other(text);
	}

	@Override
	public OutputModelObject visitLatex(BookishParser.LatexContext ctx) {
		String text = ctx.getText().trim();
		List<String> stuff = extract(latexPattern, text); // \latex{{...}}
		text = stuff.get(0);

		if ( target==Tool.Target.LATEX || target==Tool.Target.LATEX_BOOK ) {
			return new Latex(this, null, text, text);
		}

		String relativePath = "images/latex-"+md5hash(text)+".svg";
		String src = outputDir+"/"+relativePath;
		Path outpath = Paths.get(src);
		if ( !Files.exists(outpath) ) {
			Triple<String,Float,Float> results = texConverter.tex2svg(text, LATEX, BLOCK_EQN_FONT_SIZE);
			String svg = results.a;
			try {
				System.out.println(outpath);
				Files.write(outpath, svg.getBytes());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		return new Latex(this, relativePath, text, text);
	}

	@Override
	public OutputModelObject visitBlock_eqn(BookishParser.Block_eqnContext ctx) {
		String eqn = stripQuotes(ctx.getText(), 3);

		if ( target==Tool.Target.LATEX || target==Tool.Target.LATEX_BOOK ) {
			return new BlockEquation(this, null, eqn);
		}

		String relativePath = "images/blkeqn-"+md5hash(eqn)+".svg";
		String src = outputDir+"/"+relativePath;
		Path outpath = Paths.get(src);
		if ( !Files.exists(outpath) ) {
			Triple<String,Float,Float> results = texConverter.tex2svg(eqn, BLOCKEQN, BLOCK_EQN_FONT_SIZE);
			String svg = results.a;

			try {
				System.out.println(outpath);
				Files.write(outpath, svg.getBytes());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		return new BlockEquation(this, relativePath, eqn);
	}

	@Override
	public OutputModelObject visitEqn(BookishParser.EqnContext ctx) {
		String eqn = stripQuotes(ctx.getText());

		if ( target==Tool.Target.LATEX || target==Tool.Target.LATEX_BOOK ) {
			return new InlineEquation(null, eqn, -1, -1);
		}

		// check for special cases like $w$ and $\mathbf{w}_i$.
		List<String> elements = extract(eqnVarPattern, eqn);
		if ( elements.size()>0 ) {
			return new EqnVar(elements.get(0));
		}
		elements = extract(eqnVecVarPattern, eqn);
		if ( elements.size()>0 ) {
			return new EqnVecVar(elements.get(0));
		}
		elements = extract(eqnVecVarPattern2, eqn);
		if ( elements.size()>0 ) {
			return new EqnVecVar(elements.get(0));
		}
		elements = extract(eqnIndexedVarPattern, eqn);
		if ( elements.size()>0 ) {
			return new EqnIndexedVar(elements.get(0), elements.get(1));
		}
		elements = extract(eqnIndexedVecVarPattern, eqn);
		if ( elements.size()>0 ) {
			return new EqnIndexedVecVar(elements.get(0), elements.get(1));
		}
		elements = extract(eqnIndexedVecVarPattern2, eqn);
		if ( elements.size()>0 ) {
			return new EqnIndexedVecVar(elements.get(0), elements.get(1));
		}

		float height=0, depth = 0;

		String prefix = String.format("eqn-%s", md5hash(eqn));
//		String prefix = String.format("images/eqn-%s",hash(eqn));
		File[] files =
			new File(outputDir+"/images")
				.listFiles((dir, name) -> name.startsWith(prefix));
		String existing = null;
		if ( files!=null && files.length>0 ) {
			existing = files[0].getName();
			int i = existing.indexOf("-depth");
			int j = existing.indexOf(".svg", i);
			String depthS = existing.substring(i+"-depth".length(), j);
			depth = Float.parseFloat(depthS);
			return new InlineEquation("images/"+existing, eqn, -1, depth);
		}
		Triple<String,Float,Float> results =
			texConverter.tex2svg(eqn, Tex2SVG.LatexType.EQN, INLINE_EQN_FONT_SIZE);
		String svg = results.a;
		height = results.b;
		depth = results.c;
		try {
			String src = String.format("%s/images/%s-depth%06.2f.svg",outputDir,prefix,depth);
			Path outpath = Paths.get(src);
			System.out.println(outpath);
			Files.write(outpath, svg.getBytes());
			String relativePath = String.format("images/%s-depth%06.2f.svg",prefix,depth);
			return new InlineEquation(relativePath, eqn, height, depth);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}

	@Override
	public OutputModelObject visitLink(BookishParser.LinkContext ctx) {
		String txt = ctx.getText();
		int middle = txt.indexOf("]("); // e.g., [name](link)
		String title = txt.substring(1,middle);
		String href = txt.substring(middle+2,txt.length()-1);
		if ( href.contains("\\)") ) {
			href = href.replace("\\)", ")");
		}
		return new HyperLink(title,href);
	}

	@Override
	public OutputModelObject visitBlock_image(BookishParser.Block_imageContext ctx) {
		return new BlockImage(this, ctx.image().attrs().attrMap);
	}

	@Override
	public OutputModelObject visitImage(BookishParser.ImageContext ctx) {
		return new Image(this, ctx.attrs().attrMap);
	}

	@Override
	public OutputModelObject visitTable(BookishParser.TableContext ctx) {
		List<TableRow> rows = new ArrayList<>();
		TableRow headers = null;
		if ( ctx.table_header()!=null ) {
			headers = (TableRow) visitTable_header(ctx.table_header());
		}
		for (BookishParser.Table_rowContext row : ctx.table_row()) {
			rows.add( (TableRow)visit(row));
		}
		return new Table(headers, rows);
	}

	/*
	table_header : TR ws? (TH attrs END_OF_TAG table_item)+ ;
	th_tag : TH attr_assignment+ END_OF_TAG ;
	*/
	@Override
	public OutputModelObject visitTable_header(BookishParser.Table_headerContext ctx) {
		List<TableItem> items = new ArrayList<>();
		for (int i = 0; i<ctx.attrs().size(); i++) {
			BookishParser.AttrsContext attrsOfTH = ctx.attrs().get(i);
			BookishParser.Table_itemContext itemCtx = ctx.table_item().get(i);
			TableItem item = (TableItem) visit(itemCtx);
			items.add(new TableHeaderItem(item.contents,attrsOfTH.attrMap));
		}
		return new TableRow(items);
	}

	@Override
	public OutputModelObject visitTable_row(BookishParser.Table_rowContext ctx) {
		List<TableItem> items = new ArrayList<>();
		for (BookishParser.Table_itemContext el : ctx.table_item()) {
			TableItem item = (TableItem) visit(el);
			items.add(item);
		}
		return new TableRow(items);
	}

	@Override
	public OutputModelObject visitTable_item(BookishParser.Table_itemContext ctx) {
		List<OutputModelObject> contents = new ArrayList<>();
		for (ParseTree child : ctx.children) {
			contents.add( visit(child) );
		}
		return new TableItem(contents);
	}

	@Override
	public OutputModelObject visitOrdered_list(BookishParser.Ordered_listContext ctx) {
		// 		( ws? LI list_item )+
		List<ListItem> items = new ArrayList<>();
		for (BookishParser.List_itemContext el : ctx.list_item()) {
			items.add((ListItem)visit(el));
		}
		return new OrderedList(items);
	}

	@Override
	public OutputModelObject visitUnordered_list(BookishParser.Unordered_listContext ctx) {
		List<ListItem> items = new ArrayList<>();
		for (BookishParser.List_itemContext el : ctx.list_item()) {
			items.add((ListItem)visit(el));
		}
		return new UnOrderedList(items);
	}

	@Override
	public OutputModelObject visitList_item(BookishParser.List_itemContext ctx) {
		List<OutputModelObject> elements = new ArrayList<>();
		for (ParseTree el : ctx.children) {
			elements.add( visit(el) );
		}
		return new ListItem(elements);
	}

	@Override
	public OutputModelObject visitXml(BookishParser.XmlContext ctx) {
		if ( ctx.start.getType()==END_TAG ) {
			String text = ctx.getText();
			return new XMLEndTag(text.substring(2, text.length()-1));
		}
		String name = ctx.tagname.getText();
		return new XMLTag(name, ctx.attrs().attrMap);
	}

	@Override
	public OutputModelObject visitQuoted(BookishParser.QuotedContext ctx) {
		List<OutputModelObject> elements = new ArrayList<>();
		for (ParseTree el : ctx.children) {
			elements.add( visit(el) );
		}
		return new Quoted(elements);
	}

	@Override
	public OutputModelObject visitInline_code(BookishParser.Inline_codeContext ctx) {
		return new InlineCode(stripQuotes(ctx.INLINE_CODE().getText()));
	}

	@Override
	public OutputModelObject visitFirstuse(BookishParser.FirstuseContext ctx) {
		return new Italics(stripQuotes(ctx.block().getText())); // can't have markup inside
	}

	@Override
	public OutputModelObject visitTodo(BookishParser.TodoContext ctx) {
		return new TODO(stripQuotes(ctx.block().getText())); // can't have markup inside
	}

	@Override
	public OutputModelObject visitBold(BookishParser.BoldContext ctx) {
		return new Bold(stripQuotes(ctx.getText(),2));
	}

	@Override
	public OutputModelObject visitItalics(BookishParser.ItalicsContext ctx) {
		return new Italics(stripQuotes(ctx.getText()));
	}

	@Override
	public OutputModelObject visitChapquote(BookishParser.ChapquoteContext ctx) {
		return new ChapQuote((TextBlock) visit(ctx.q), (TextBlock) visit(ctx.a));
	}

	@Override
	public OutputModelObject visitLinebreak(BookishParser.LinebreakContext ctx) {
		return new LineBreak();
	}

	@Override
	public OutputModelObject visitSite(BookishParser.SiteContext ctx) {
		String label = null;
		EntityDef def = null;
		if ( ctx.REF()!=null ) {
			label = stripQuotes(ctx.REF().getText());
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
				return null;
			}
		}
		def.model = new Site((SiteDef)def);
		return null;
	}

	@Override
	public OutputModelObject visitCitation(BookishParser.CitationContext ctx) {
		String label = null;
		EntityDef def = null;
		if ( ctx.REF()!=null ) {
			label = stripQuotes(ctx.REF().getText());
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
				return null;
			}
		}
		def.model = new Citation(def, label, (TextBlock)visit(ctx.t), (TextBlock) visit(ctx.a));
		return null;
	}

	@Override
	public OutputModelObject visitSidequote(BookishParser.SidequoteContext ctx) {
		String label = null;
		EntityDef def = null;
		if ( ctx.REF()!=null ) {
			label = stripQuotes(ctx.REF().getText());
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
			}
		}
		SideQuote q = new SideQuote(def, label, (TextBlock) visit(ctx.q), (TextBlock) visit(ctx.a));
		if ( def!=null ) {
			def.model = q;
		}
		if ( label==null ) {
			return q; // if no label, insert inline here
		}
		return null;
	}

	@Override
	public OutputModelObject visitSidenote(BookishParser.SidenoteContext ctx) {
		String label = null;
		EntityDef def = null;
		if ( ctx.REF()!=null ) {
			label = stripQuotes(ctx.REF().getText());
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
			}
		}
		SideNote q = new SideNote(def, label, (TextBlock) visit(ctx.block));
		if ( def!=null ) {
			def.model = q;
		}
		if ( label==null ) {
			return q; // if no label, insert inline here
		}
		return null;
	}

	// figure    : FIGURE attrs END_OF_TAG paragraph_content END_FIGURE
	@Override
	public OutputModelObject visitSidefig(BookishParser.SidefigContext ctx) {
		String label = ctx.attrs.attrMap.get("label");
		EntityDef def = null;
		if ( label!=null ) {
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
				return null;
			}
		}
		Paragraph p = (Paragraph)visit(ctx.paragraph_content());
		TextBlock figText = new TextBlock(p.elements);
		SideFigure f = new SideFigure(def, label, figText, ctx.attrs.attrMap);
		if ( def!=null ) {
			def.model = f;
		}
		return null; // a ref will make this appear
	}

	@Override
	public OutputModelObject visitCallout(BookishParser.CalloutContext ctx) {
		return new Callout((TextBlock)visit(ctx.block()));
	}

	@Override
	public OutputModelObject visitAside(BookishParser.AsideContext ctx) {
		Join content = (Join)visit(ctx.section_content());
		TextBlock text = new TextBlock(content.elements);
		return new Aside(text, ctx.attrs().attrMap);
	}

	@Override
	public OutputModelObject visitBlock(BookishParser.BlockContext ctx) {
		Paragraph content = (Paragraph)visit(ctx.paragraph_content());
		return new TextBlock(content.elements);
	}

	@Override
	public OutputModelObject visitRef(BookishParser.RefContext ctx) {
		String label = stripQuotes(ctx.REF().getText());
		EntityDef def = document.getEntity(label);
		System.out.println("Ref to "+def);
		if ( def==null ) {
			System.err.printf("line %d: Unknown label '%s'\n", ctx.start.getLine(), label);
			return new UnknownRef(ctx.REF().getSymbol());
		}

		Class<? extends EntityRef> refClass = defToRefMap.get(def.getClass());
		try {
			Constructor<? extends EntityRef> ctor = refClass.getConstructor(EntityDef.class);
			EntityRef entityRef = ctor.newInstance(def);
			return entityRef;
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
		}
		return null;
	}

	/** ```foo``` Just show it */
	@Override
	public OutputModelObject visitPycode(BookishParser.PycodeContext ctx) {
		return new BlockCode(stripQuotes(ctx.getText(),3).trim());
	}

	/** \pyfig[label]{draw some stuff} */
	@Override
	public OutputModelObject visitPyfig(BookishParser.PyfigContext ctx) {
		return new PyFig(this, ctx.codeDef, ctx.stdout, ctx.stderr, ctx.codeDef.argMap);
	}

	/** \pyeval[label,hide]{notebook cell}{displayExpr} */
	@Override
	public OutputModelObject visitPyeval(BookishParser.PyevalContext ctx) {
		Map<String, String> args = ctx.codeDef.argMap;
		if ( ctx.displayData!=null ) {
			String[] dataA = ctx.displayData.split("\n");
			String type = dataA[0];
			String data = null;
			if ( dataA.length>1 ) {
				data = join(Arrays.copyOfRange(dataA, 1, dataA.length), "\n");
			}
			if ( type.equals("DataFrame") ) {
				DataTable dataTable = new DataTable(data);
				return new PyEvalDataFrame(ctx.codeDef, ctx.stdout, ctx.stderr, args, type, dataTable);
			}
			else {
				return new PyEval(ctx.codeDef, ctx.stdout, ctx.stderr, args, type, data);
			}
		}
		else {
			return new PyEval(ctx.codeDef, ctx.stdout, ctx.stderr, args, null, null);
		}
	}

	@Override
	public OutputModelObject visitInline_pyeval(BookishParser.Inline_pyevalContext ctx) {
		if ( ctx.displayData!=null ) {
			String[] dataA = ctx.displayData.split("\n");
			String type = dataA[0];
			String data = null;
			if ( dataA.length>1 ) {
				data = join(Arrays.copyOfRange(dataA, 1, dataA.length), "\n");
				if ( dataA.length==2 && type.startsWith("float") ) {
					data = String.format("%.4f", Double.parseDouble(dataA[1]));
				}
			}
			return new InlinePyEval(ctx.codeDef, ctx.stdout, ctx.stderr, type, data);
		}
		return null;
	}

	public boolean isHTMLTarget() {
		return templates.getName().startsWith("HTML");
	}

	public boolean isLatexTarget() {
		return templates.getName().startsWith("latex");
	}

	// Support

	public String processImageWidth(String width) {
		if ( width!=null ) {
			if ( width.endsWith("%") ) {
				// drop %; latex templates will use \linewidth and html will put % back on
				width = width.replace("%", "");
			}
			else {
				System.err.println("PYFIG width "+width+" must be in % units");
				width = "100";
			}
			if ( isLatexTarget() ) {
				width = String.valueOf(Float.valueOf(width)/100.0); // convert to 0..1
			}
			else {
				width = width + "%";
			}
		}
		return width;
	}

	public static List<String> extract(Pattern pattern, String text) {
		Matcher m = pattern.matcher(text);
		List<String> elements = new ArrayList<>();
		if ( m.matches() ) {
			for (int i = 1; i <= m.groupCount(); i++) {
				elements.add(m.group(i));
			}
		}
		return elements;
	}

	public static Pair<String,String> splitSectionTitle(String title) {
		List<String> anchors = extract(sectionAnchorPattern, title);
		String anchor = null;
		if ( anchors.size()>0 ) {
			anchor = anchors.get(0);
			int lparent = title.indexOf('(');
			title = title.substring(0, lparent).trim();
		}
		return new Pair<>(title,anchor);
	}

	public EntityDef lookupEntity(TerminalNode refNode) {
		EntityDef def = null;
		if ( refNode!=null ) {
			String label = stripQuotes(refNode.getText());
			def = document.getEntity(label);
			if ( def==null ) {
				System.err.printf("line %d: Unknown label '%s'\n", refNode.getSymbol().getLine(), label);
			}
		}
		return def;
	}

	public static String stripCurlies(String s) {
		if ( s!=null && (s.startsWith("{") || s.startsWith("[")) ) {
			return stripQuotes(s);
		}
		return s;
	}

	public static Map<String,String> parseXMLAttrs(String attrs) {
		XMLLexer lexer = new XMLLexer(CharStreams.fromString(attrs));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		XMLParser parser = new XMLParser(tokens);
		XMLParser.AttrsContext attrsTree = parser.attrs();
		Map<String,String> attrMap = attrsTree.attrMap;
		return attrMap;
	}

	/*
	public static Map<String,String> parseXMLAttrs(String attrs) {
		Map<String,String> m = new LinkedHashMap<>();
		StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(attrs));
		Token t = getToken(tokenizer);
		while ( t.getType()==TT_WORD ) {
			String key = t.getText();
			t = getToken(tokenizer);

		}
		tokenizer.quoteChar('"');
		tokenizer.eolIsSignificant(false);
	}

	public static Token getToken(StreamTokenizer tokenizer) {
		try {
			while ( tokenizer.nextToken()!=TT_EOF ) {
				switch ( tokenizer.ttype ) {
					case TT_EOF :
						return new CommonToken(TT_EOF, "");
					case TT_WORD :
						return new CommonToken(TT_EOF, tokenizer.sval);
					case TT_NUMBER :
						return new CommonToken(TT_EOF, String.valueOf(tokenizer.nval));
					case ':' :
						return new CommonToken(':', ":");
					case '"' :
						return new CommonToken('"', tokenizer.sval);
					default :
						System.err.println("Invalid char in xml attrs: "+(char)(tokenizer.ttype));
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	*/
}

