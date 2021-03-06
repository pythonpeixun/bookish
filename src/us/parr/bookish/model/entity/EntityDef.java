package us.parr.bookish.model.entity;

import org.antlr.v4.runtime.Token;
import org.stringtemplate.v4.ST;
import us.parr.bookish.model.OutputModelObject;

import java.util.Objects;

import static us.parr.lib.ParrtStrings.stripQuotes;

public class EntityDef {
	public String label; // optional label
	public Token startToken;

	/** indexed from 1; fig, citation, sidenote number.
	 *  Does not track chp/sec numbers. Those are done via
	 *  ContainerWithTitle.connectContainerTree().
	 */
	public int index;

	public OutputModelObject model;
	public ST template;

	public EntityDef(int index, Token refOrStartToken) {
		this.index = index;
		this.startToken = refOrStartToken;
		if ( startToken!=null && startToken.getText().startsWith("[") ) {
			this.label = stripQuotes(refOrStartToken.getText());
		}
	}

	public boolean isGloballyVisible() { return false; }

	public Token getStartToken() { return startToken; }

	public boolean isSideItem() { return false; }

	@Override
	public boolean equals(Object o) {
		if ( this==o ) return true;
		if ( !(o instanceof EntityDef) ) return false;
		EntityDef entityDef = (EntityDef) o;
		return Objects.equals(label, entityDef.label);
	}

	@Override
	public int hashCode() {
		return Objects.hash(label);
	}

	@Override
	public String toString() {
		return '<'+label+":"+this.getClass().getSimpleName()+'>';
	}
}
