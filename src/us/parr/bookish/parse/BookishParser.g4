parser grammar BookishParser;

@header {
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Arrays;

import us.parr.lib.ParrtStrings;
import us.parr.lib.ParrtCollections;
import us.parr.lib.ParrtIO;
import us.parr.bookish.model.entity.*;
import us.parr.lib.collections.MutableInt;
import static us.parr.bookish.translate.Translator.splitSectionTitle;
import static us.parr.bookish.translate.Translator.parseXMLAttrs;
}

options {
	tokenVocab=BookishLexer;
}

@members {
	/** Global labeled entities such as citations, figures, websites.
	 *  Collected from all input markdown files.
	 *
	 *  Track all labeled entities in this file for inclusion in overall book.
	 *  Do during parse for speed, to avoid having to walk tree 2x.
	 */
	public Map<String,EntityDef> entities = new LinkedHashMap<>();

	public void defEntity(EntityDef entity) {
		if ( entity.label!=null ) {
			if ( entities.containsKey(entity.label) ) {
				System.err.printf("line %d: redefinition of label %s\n",
				 entity.getStartToken().getLine(), entity.label);
			}
			entities.put(entity.label, entity);
			System.out.println("Def "+entity);
		}
	}

	// Each parser (usually per doc/chapter) keeps its own counts for sections, figures, sidenotes, web links, ...

	public int defCounter = 1;
	public int figCounter = 1; // track 1..n for whole chapter.
	public int secCounter = 1;
	public int subSecCounter = 1;
	public int subSubSecCounter = 1;

	public ChapterDef currentChap;
	public SectionDef currentSec;
	public SectionDef currentSecPtr;
	public SubSectionDef currentSubSec;
	public SubSubSectionDef currentSubSubSec;

	public List<ExecutableCodeDef> codeBlocks = new ArrayList<>();
	public Map<String,MutableInt> codeCounters = new HashMap<>();

	public String inputFilename;
	public int chapNumber;

	public BookishParser(TokenStream input, String inputFilename, int chapNumber) {
		this(input);
		this.inputFilename = inputFilename;
		this.chapNumber = chapNumber;
	}
}

document
	:	chapter BLANK_LINE? EOF
	;

chapter : BLANK_LINE? chap=CHAPTER
		  {
		  currentChap = new ChapterDef(chapNumber, $chap, null);
		  defEntity(currentChap);
		  }
		  author? preabstract? abstract_? section_content section*
		;

author : (ws|BLANK_LINE)? AUTHOR LCURLY paragraph_optional_blank_line RCURLY ;

abstract_ : (ws|BLANK_LINE)? ABSTRACT LCURLY paragraph_optional_blank_line paragraph* RCURLY;

preabstract : (ws|BLANK_LINE)? PREABSTRACT LCURLY paragraph_optional_blank_line paragraph* RCURLY;

section : BLANK_LINE sec=SECTION
		  {
		  subSecCounter = 1;
		  subSubSecCounter = 1;
		  currentSubSec = null;
		  currentSubSubSec = null;
		  currentSec = new SectionDef(secCounter, $sec, currentChap);
		  currentSecPtr = currentSec;
		  defEntity(currentSec);
		  secCounter++;
		  }
		  section_content subsection*
		;

subsection : BLANK_LINE sec=SUBSECTION
		  {
		  subSubSecCounter = 1;
		  currentSubSubSec = null;
		  currentSubSec = new SubSectionDef(subSecCounter, $sec, currentSec);
		  currentSecPtr = currentSubSec;
		  defEntity(currentSubSec);
		  subSecCounter++;
		  }
		  section_content subsubsection*
		;

subsubsection : BLANK_LINE sec=SUBSUBSECTION
		  {
		  currentSubSubSec = new SubSubSectionDef(subSubSecCounter, $sec, currentSubSec);
		  currentSecPtr = currentSubSubSec;
		  defEntity(currentSubSubSec);
		  subSubSecCounter++;
		  }
		  section_content
		;

section_content : (section_element|ws)* ;

section_element
	:	paragraph
	|	BLANK_LINE?
	 	(	link
		|	eqn
		|	block_eqn
		|	ordered_list
		|	unordered_list
		|	table
		|	block_image
		|	latex
		|	xml
		|	site
		|	citation
		|	sidequote
		|	sidenote
		|	chapquote
		|	sidefig
		|	figure
		|	pycode
		|	pyfig
		|	pyeval
		|	callout
		|	aside
		)
	|	other
	;

site      : SITE REF ws? block
			{defEntity(new SiteDef(defCounter++, $REF, $block.text));}
		  ;

citation  : CITATION REF ws? t=block ws? a=block
			{defEntity(new CitationDef(defCounter++, $REF, $t.text, $a.text));}
		  ;

chapquote : CHAPQUOTE q=block ws? a=block
		  ;

sidequote : SIDEQUOTE (REF ws?)? q=block ws? a=block
			{if ($REF!=null) defEntity(new SideQuoteDef(defCounter++, $REF, $q.text, $a.text));}
		  ;

sidenote  : SIDENOTE (REF ws?)? block
			{if ($REF!=null) defEntity(new SideNoteDef(defCounter++, $REF, $block.text));}
		  ;

sidefig   : SIDEFIG attrs END_OF_TAG paragraph_content END_SIDEFIG
			{
			if ( $attrs.attrMap.containsKey("label") ) {
				defEntity(new SideFigDef(figCounter, $SIDEFIG, $attrs.attrMap));
			}
			else {
				System.err.println("line "+$SIDEFIG.line+": sidefig missing label attribute");
			}
			figCounter++;
			}
		  ;

figure    : FIGURE attrs END_OF_TAG paragraph_content END_FIGURE
			{
			if ( $attrs.attrMap.containsKey("label") ) {
				defEntity(new FigureDef(figCounter, $FIGURE, $attrs.attrMap));
			}
			else {
				System.err.println("line "+$FIGURE.line+": figure missing label attribute");
			}
			figCounter++;
			}
		  ;

aside	  : ASIDE attrs END_OF_TAG section_content BLANK_LINE? END_ASIDE ;

callout   : CALLOUT block
		  ;

pycode    : CODEBLOCK ;

pyfig returns [PyFigDef codeDef, String stdout, String stderr]
	:	PYFIG codeblock END_PYFIG
		{
		String tag = $PYFIG.text;
		tag = tag.substring("<pyfig".length());
		tag = tag.substring(0,tag.length()-1); // strip '>'
		Map<String,String> args = parseXMLAttrs(tag);
		String fname = ParrtIO.basename(inputFilename);
		String py = $codeblock.text.trim();
		String label = args.get("label");
		codeCounters.putIfAbsent(label, new MutableInt(1));
		if ( py.length()>=0 ) {
			$codeDef = new PyFigDef($ctx, fname, codeCounters.get(label).v, args, py);
			codeBlocks.add($codeDef);
			$codeDef.enclosingSection = currentSecPtr;
			$codeDef.enclosingChapter = currentChap;
		}
		codeCounters.get(label).v++;
		}
	;

/** <pyeval args>code to exec</pyeval>
 *  Must manually parse args and extract code from string
 */
pyeval returns [PyEvalDef codeDef, String stdout, String stderr, String displayData]
    :	PYEVAL codeblock END_PYEVAL
		{
		String tag = $PYEVAL.text;
		tag = tag.substring("<pyeval".length());
		tag = tag.substring(0,tag.length()-1); // strip '>'
		Map<String,String> args = parseXMLAttrs(tag);
		String fname = ParrtIO.basename(inputFilename);
		// last line is expression to get output or blank line or comment
		String py = null;
		String label = args.get("label");
		codeCounters.putIfAbsent(label, new MutableInt(1));
		if ( $codeblock.ctx!=null ) {
			py = $codeblock.text.trim();
			if ( py.length()==0 ) py = null;
		}
		$codeDef = new PyEvalDef($ctx, fname, codeCounters.get(label).v, args, py);
		codeBlocks.add($codeDef);
		$codeDef.enclosingSection = currentSecPtr;
		$codeDef.enclosingChapter = currentChap;
		codeCounters.get(label).v++;
		}
	;

codeblock : PYCODE_CONTENT* ;

/** \pyfig[label,hide=true,width="20em"]{...}
 *  \pyfig[width="20em"]{...}
 *  \pyfig[label]{...}
codeblock_args returns [Map<String,String> argMap = new LinkedHashMap<>()]
	:	START_CODE_BLOCK_ARGS
		(	l=CODE_BLOCK_ATTR CODE_BLOCK_COMMA
			codeblock_arglist[$argMap] ( CODE_BLOCK_COMMA codeblock_arglist[$argMap] )*
			{$argMap.put("label", $l.text.trim());}

		|	l=CODE_BLOCK_ATTR
			{$argMap.put("label", $l.text.trim());}

   		|	codeblock_arglist[$argMap] ( CODE_BLOCK_COMMA codeblock_arglist[$argMap] )*
		)
		END_CODE_BLOCK_ARGS
	;

codeblock_arglist[Map<String,String> argMap]
	:	name=CODE_BLOCK_ATTR CODE_BLOCK_EQ (value=CODE_BLOCK_ATTR_VALUE|value=CODE_BLOCK_ATTR)
    	{
    	String v = $value.text;
    	if ( v.startsWith("\"") ) v = ParrtStrings.stripQuotes(v);
    	$argMap.put($name.text,v);
    	}
    ;
 */

block : LCURLY paragraph_content? RCURLY ;

paragraph
	:	BLANK_LINE paragraph_content
	;

paragraph_optional_blank_line
	:	BLANK_LINE? paragraph_content
	;

paragraph_content
	:	(paragraph_element|quoted|ws)+
	;

paragraph_element
	:	eqn
    |	link
    |	italics
    |	bold
    |	image
	|	xml
	|	ref
	|	symbol
	|	firstuse
	|	todo
	|	inline_code
	|	inline_pyeval
	|	pyfig
	|	pyeval
	|	linebreak
	|	other
	;

ref : REF ;

linebreak : LINE_BREAK ;

symbol : SYMBOL block ; // e.g., \symbol{degree}, \symbol{tm}

quoted : QUOTE (paragraph_element|ws)+ QUOTE ;

inline_code : INLINE_CODE ;

inline_pyeval returns [InlinePyEvalDef codeDef, String stdout, String stderr, String displayData]
 	:	INLINE_PYEVAL REF? CHUNK
		{
		String fname = ParrtIO.basename(inputFilename);
		String tag = $REF.text;
		tag = ParrtStrings.stripQuotes(tag);
		// last line is expression to get output or blank line or comment
		String py = $CHUNK.text.trim();
		py = ParrtStrings.stripQuotes(py, 2).trim(); // strip double curlies
		codeCounters.putIfAbsent(tag, new MutableInt(1));
		if ( py.length()==0 ) py = null;
		$codeDef = new InlinePyEvalDef($ctx, fname, codeCounters.get(tag).v, $REF!=null?$REF:$INLINE_PYEVAL, py);
		codeBlocks.add($codeDef);
		codeCounters.get(tag).v++;
		}
	;

firstuse : FIRSTUSE block ;

todo : TODO block ;

latex : LATEX ;

ordered_list
	:	OL
		( ws? LI ws? list_item )+ ws?
		OL_
	;

unordered_list
	:	UL
		( ws? LI ws? list_item )+ ws?
		UL_
	;

table
	:	TABLE
			( ws? table_header )? // header row
			( ws? table_row )+ // actual rows
			ws?
		TABLE_
	;

table_header : TR (ws? TH attrs END_OF_TAG table_item)+ ;
table_row : TR (ws? TD table_item)+ ;

list_item : (section_element|paragraph_element|quoted|ws|BLANK_LINE)* ;

table_item : (section_element|paragraph_element|quoted|ws|BLANK_LINE)* ;

block_image : image ;

image : IMG attrs END_OF_TAG ;

attrs returns [Map<String,String> attrMap = new LinkedHashMap<>()]
 	:	attr_assignment[$attrMap]*
 	;

attr_assignment[Map<String,String> attrMap]
	:	name=XML_ATTR XML_EQ value=(XML_ATTR|XML_ATTR_VALUE|XML_ATTR_NUM)
		{
    	String v = $value.text;
    	if ( v.startsWith("\"") ) v = ParrtStrings.stripQuotes(v);
		$attrMap.put($name.text,v);
		}
	;

xml	: XML tagname=XML_ATTR attrs END_OF_TAG | END_TAG ;

link 		:	LINK ;
italics 	:	ITALICS ;
bold 		:	BOLD ;
other       :	OTHER | POUND | DOLLAR ;

block_eqn : BLOCK_EQN ;

eqn : EQN ;

ws : (SPACE | NL | TAB)+ ;
