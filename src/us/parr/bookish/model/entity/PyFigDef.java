package us.parr.bookish.model.entity;

import us.parr.bookish.parse.BookishParser;

import java.util.Map;

public class PyFigDef extends ExecutableCodeDef {
	public String generatedFilenameNoSuffix; // minus suffix; could be .svg, .png, or .pdf

	public PyFigDef(BookishParser.PyfigContext tree,
	                String inputFilename,
	                int index,
	                Map<String,String> argMap,
	                String code)
	{
		super(tree, inputFilename, index, tree.getStart(), argMap, code);
		this.isOutputVisible = false;
	}
}
