/**
 * This file Copyright (c) 2005-2010 Aptana, Inc. This program is
 * dual-licensed under both the Aptana Public License and the GNU General
 * Public license. You may elect to use one or the other of these licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT. Redistribution, except as permitted by whichever of
 * the GPL or APL you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or modify this
 * program under the terms of the GNU General Public License,
 * Version 3, as published by the Free Software Foundation.  You should
 * have received a copy of the GNU General Public License, Version 3 along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Aptana provides a special exception to allow redistribution of this file
 * with certain other free and open source software ("FOSS") code and certain additional terms
 * pursuant to Section 7 of the GPL. You may view the exception and these
 * terms on the web at http://www.aptana.com/legal/gpl/.
 *
 * 2. For the Aptana Public License (APL), this program and the
 * accompanying materials are made available under the terms of the APL
 * v1.0 which accompanies this distribution, and is available at
 * http://www.aptana.com/legal/apl/.
 *
 * You may view the GPL, Aptana's exception and additional terms, and the
 * APL in the file titled license.html at the root of the corresponding
 * plugin containing this source file.
 *
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.css.contentassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.swt.graphics.Image;

import com.aptana.editor.common.AbstractThemeableEditor;
import com.aptana.editor.common.CommonContentAssistProcessor;
import com.aptana.editor.common.contentassist.CommonCompletionProposal;
import com.aptana.editor.common.contentassist.LexemeProvider;
import com.aptana.editor.common.contentassist.UserAgentManager;
import com.aptana.editor.css.Activator;
import com.aptana.editor.css.CSSScopeScanner;
import com.aptana.editor.css.contentassist.index.CSSIndexConstants;
import com.aptana.editor.css.contentassist.model.ElementElement;
import com.aptana.editor.css.contentassist.model.PropertyElement;
import com.aptana.editor.css.contentassist.model.ValueElement;
import com.aptana.editor.css.parsing.lexer.CSSTokenType;
import com.aptana.parsing.lexer.IRange;
import com.aptana.parsing.lexer.Lexeme;
import com.aptana.parsing.lexer.Range;

public class CSSContentAssistProcessor extends CommonContentAssistProcessor
{
	/**
	 * LocationType
	 */
	static enum LocationType
	{
		ERROR, OUTSIDE_RULE, INSIDE_RULE, INSIDE_ARG, INSIDE_PROPERTY, INSIDE_VALUE
	};

	private static final Image ELEMENT_ICON = Activator.getImage("/icons/element.gif"); //$NON-NLS-1$
	private static final Image PROPERTY_ICON = Activator.getImage("/icons/property.gif"); //$NON-NLS-1$

	private IContextInformationValidator _validator;
	private CSSIndexQueryHelper _queryHelper;
	private Lexeme<CSSTokenType> _currentLexeme;
	private IRange _replaceRange;

	/**
	 * CSSContentAssistProcessor
	 * 
	 * @param editor
	 */
	public CSSContentAssistProcessor(AbstractThemeableEditor editor)
	{
		super(editor);

		this._queryHelper = new CSSIndexQueryHelper();
	}

	/**
	 * getElementProposals
	 * 
	 * @param proposals
	 * @param offset
	 */
	protected void addAllElementProposals(List<ICompletionProposal> proposals, int offset)
	{
		List<ElementElement> elements = this._queryHelper.getElements();

		if (elements != null)
		{
			for (ElementElement element : elements)
			{
				String description = CSSModelFormatter.getDescription(element);
				String[] userAgents = element.getUserAgentNames();
				Image[] userAgentIcons = UserAgentManager.getInstance().getUserAgentImages(userAgents);

				this.addProposal(proposals, element.getName(), ELEMENT_ICON, description, userAgentIcons, offset);
			}
		}
	}

	/**
	 * getAllPropertyProposals
	 * 
	 * @param proposals
	 * @param offset
	 */
	protected void addAllPropertyProposals(List<ICompletionProposal> proposals, LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		List<PropertyElement> properties = this._queryHelper.getProperties();

		if (properties != null)
		{
			if (this._currentLexeme != null)
			{
				// don't replace the semicolon when inserting a new property name
				switch (this._currentLexeme.getType())
				{
					case COLON:
						this._replaceRange = this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset - 1);
						break;
						
					case SEMICOLON:
					case CURLY_BRACE:
						this._replaceRange = this._currentLexeme = null;
						break;
						
					case PROPERTY:
						if (offset == this._currentLexeme.getStartingOffset())
						{
							this._replaceRange = this._currentLexeme = null;
						}
						break;
						
					default:
						if (this._currentLexeme.contains(offset) == false && this._currentLexeme.getEndingOffset() != offset - 1)
						{
							this._replaceRange = this._currentLexeme = null;
						}
						break;
				}
			}
			
			for (PropertyElement property : properties)
			{
				String description = CSSModelFormatter.getDescription(property);
				String[] userAgents = property.getUserAgentNames();
				Image[] userAgentIcons = UserAgentManager.getInstance().getUserAgentImages(userAgents);

				this.addProposal(proposals, property.getName(), PROPERTY_ICON, description, userAgentIcons, offset);
			}
		}
	}

	/**
	 * addClasses
	 * 
	 * @param proposals
	 * @param offset
	 */
	protected void addClasses(List<ICompletionProposal> proposals, int offset)
	{
		Map<String, String> classes = this._queryHelper.getClasses(this.getIndex());

		if (classes != null)
		{
			UserAgentManager manager = UserAgentManager.getInstance();
			String[] userAgents = manager.getActiveUserAgentIDs(); // classes can be used by all user agents
			Image[] userAgentIcons = manager.getUserAgentImages(userAgents);

			for (Entry<String, String> entry : classes.entrySet())
			{
				this.addProposal(proposals, "." + entry.getKey(), ELEMENT_ICON, null, userAgentIcons, offset); //$NON-NLS-1$
			}
		}
	}

	/**
	 * addIDs
	 * 
	 * @param result
	 * @param offset
	 */
	protected void addIDs(List<ICompletionProposal> proposals, int offset)
	{
		Map<String, String> ids = this._queryHelper.getIDs(this.getIndex());

		if (ids != null)
		{
			UserAgentManager manager = UserAgentManager.getInstance();
			String[] userAgents = manager.getActiveUserAgentIDs(); // classes can be used by all user agents
			Image[] userAgentIcons = manager.getUserAgentImages(userAgents);

			for (Entry<String, String> entry : ids.entrySet())
			{
				this.addProposal(proposals, "#" + entry.getKey(), ELEMENT_ICON, null, userAgentIcons, offset); //$NON-NLS-1$
			}
		}
	}

	/**
	 * addInsideRuleProposals
	 * 
	 * @param proposals
	 * @param document
	 * @param offset
	 */
	private void addInsideRuleProposals(List<ICompletionProposal> proposals, LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		LocationType location = this.getInsideLocationType(lexemeProvider, offset);

		switch (location)
		{
			case INSIDE_PROPERTY:
				this.addAllPropertyProposals(proposals, lexemeProvider, offset);
				break;

			case INSIDE_VALUE:
				this.addPropertyValues(proposals, lexemeProvider, offset);
				break;

			default:
				break;
		}
	}

	/**
	 * addOutsideRuleProposals
	 * 
	 * @param proposals
	 * @param document
	 * @param offset
	 */
	private void addOutsideRuleProposals(List<ICompletionProposal> proposals, LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		if (this._currentLexeme != null)
		{
			switch (this._currentLexeme.getType())
			{
				case COMMA:
					int index = lexemeProvider.getLexemeCeilingIndex(offset);
					this._replaceRange = this._currentLexeme = lexemeProvider.getLexeme(index + 1);
					break;
				
				case CURLY_BRACE:
					this._replaceRange = this._currentLexeme = null;
					offset++;
					break;
					
				case ELEMENT:
				case IDENTIFIER:
					if (offset == this._currentLexeme.getStartingOffset())
					{
						this._replaceRange = this._currentLexeme = null;
					}
					break;
					
				default:
					break;
			}
		}
		
		if (this._currentLexeme != null)
		{
			switch (this._currentLexeme.getType())
			{
				case CLASS:
					this.addClasses(proposals, offset);
					break;
	
				case ID:
					this.addIDs(proposals, offset);
					break;
	
				default:
					this.addAllElementProposals(proposals, offset);
					break;
			}
		}
		else
		{
			this.addAllElementProposals(proposals, offset);
		}
	}

	/**
	 * addPropertyValues
	 * 
	 * @param proposals
	 * @param lexemeProvider
	 * @param offset
	 */
	private void addPropertyValues(List<ICompletionProposal> proposals, LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		// get property name
		String propertyName = this.getPropertyName(lexemeProvider, offset);
		
		if (propertyName != null && propertyName.length() > 0)
		{
			this.setPropertyValueRange(lexemeProvider, offset);

			// lookup value list for property
			PropertyElement property = this._queryHelper.getProperty(propertyName);
			
			if (property != null)
			{
				Image[] userAgentIcons = UserAgentManager.getInstance().getUserAgentImages(property.getUserAgentNames());
		
				// build proposals from value list
				for (ValueElement value : property.getValues())
				{
					this.addProposal(proposals, value.getName(), PROPERTY_ICON, value.getDescription(), userAgentIcons, offset);
				}
			}
		}
	}

	/**
	 * addProposal
	 * 
	 * @param proposals
	 * @param name
	 * @param icon
	 * @param userAgents
	 * @param offset
	 */
	private void addProposal(List<ICompletionProposal> proposals, String name, Image image, String description, Image[] userAgents, int offset)
	{
		int length = name.length();
		String displayName = name;
		IContextInformation contextInfo = null;
		int replaceLength = 0;

		if (this._replaceRange != null)
		{
			offset = this._replaceRange.getStartingOffset();
			replaceLength = this._replaceRange.getLength();
		}

		// build proposal
		CommonCompletionProposal proposal = new CommonCompletionProposal(name, offset, replaceLength, length, image, displayName, contextInfo, description);
		proposal.setFileLocation(CSSIndexConstants.CORE);
		proposal.setUserAgentImages(userAgents);

		// add it to the list
		proposals.add(proposal);
	}
	
	/*
	 * (non-Javadoc)
	 * @see
	 * com.aptana.editor.common.CommonContentAssistProcessor#computeCompletionProposals(org.eclipse.jface.text.ITextViewer
	 * , int, char, boolean)
	 */
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset, char activationChar, boolean autoActivated)
	{
		// tokenize the current document
		IDocument document = viewer.getDocument();
		LexemeProvider<CSSTokenType> lexemeProvider = this.createLexemeProvider(document, offset);

		// store a reference to the lexeme at the current position
		this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset);
		
		// if nothing's there, see if we're touching a lexeme to the left of the
		// offset
		if (this._currentLexeme == null)
		{
			this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset - 1);
		}
		
		// replace the current lexeme by default. This may be adjusted as the
		// CA context is fine-tuned below
		this._replaceRange = this._currentLexeme;

		// first step is to determine if we're inside our outside of a rule
		LocationType location = this.getCoarseLocationType(lexemeProvider, offset);

		// create proposal container
		List<ICompletionProposal> result = new ArrayList<ICompletionProposal>();
		
		switch (location)
		{
			case OUTSIDE_RULE:
				this.addOutsideRuleProposals(result, lexemeProvider, offset);
				break;

			case INSIDE_RULE:
				this.addInsideRuleProposals(result, lexemeProvider, offset);
				break;

			case INSIDE_ARG:
				// TODO: lookup specific property and shows its values
				break;

			default:
				break;
		}

		// sort by display name
		Collections.sort(result, new Comparator<ICompletionProposal>()
		{
			@Override
			public int compare(ICompletionProposal o1, ICompletionProposal o2)
			{
				return o1.getDisplayString().compareToIgnoreCase(o2.getDisplayString());
			}
		});

		// select the current proposal based on the current lexeme
		if (this._currentLexeme != null)
		{
			this.setSelectedProposal(this._currentLexeme.getText(), result);
		}

		// return results
		return result.toArray(new ICompletionProposal[result.size()]);
	}

	/**
	 * createLexemeProvider
	 * 
	 * @param document
	 * @param offset
	 * @return
	 */
	LexemeProvider<CSSTokenType> createLexemeProvider(IDocument document, int offset)
	{
		return new LexemeProvider<CSSTokenType>(document, offset, new CSSScopeScanner())
		{
			@Override
			protected CSSTokenType getTypeFromData(Object data)
			{
				return CSSTokenType.get((String) data);
			}
		};
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	@Override
	public char[] getCompletionProposalAutoActivationCharacters()
	{
		// TODO: these should be defined in a preference page
		return new char[] { '.', '#', ':', '\t' };
		// return new char[] { ':', '\t', '{', ';' };
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	@Override
	public IContextInformationValidator getContextInformationValidator()
	{
		if (this._validator == null)
		{
			this._validator = new CSSContextInformationValidator();
		}

		return this._validator;
	}

	/**
	 * getIndexOfPreviousColon
	 * 
	 * @param lexemeProvider
	 * @param offset
	 * @return
	 */
	private int getIndexOfPreviousColon(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		int index = lexemeProvider.getLexemeFloorIndex(offset);
		int result = -1;

		for (int i = index; i >= 0; i--)
		{
			Lexeme<CSSTokenType> lexeme = lexemeProvider.getLexeme(i);

			if (lexeme.getType() == CSSTokenType.COLON)
			{
				result = i;
				break;
			}
		}
		
		return result;
	}

	/**
	 * getInsideLocation
	 * 
	 * @param document
	 * @param offset
	 * @return
	 */
	LocationType getInsideLocationType(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		LocationType location = LocationType.ERROR;
		
		int index = lexemeProvider.getLexemeIndex(offset);
		
		if (index < 0)
		{
			int candidateIndex = lexemeProvider.getLexemeFloorIndex(offset);
			Lexeme<CSSTokenType> lexeme = lexemeProvider.getLexeme(candidateIndex);
			
			if (lexeme != null && lexeme.getEndingOffset() == offset - 1)
			{
				index = candidateIndex;
			}
			else
			{
				index = lexemeProvider.getLexemeCeilingIndex(offset);
			}
		}

		while (index >= 0)
		{
			Lexeme<CSSTokenType> lexeme = lexemeProvider.getLexeme(index);

			switch (lexeme.getType())
			{
				case CURLY_BRACE:
					if ("{".equals(lexeme.getText())) //$NON-NLS-1$
					{
						location = LocationType.INSIDE_PROPERTY;
					}
					else
					{
						if (index > 0)
						{
							Lexeme<CSSTokenType> previousLexeme = lexemeProvider.getLexeme(index - 1);
							
							if (previousLexeme.getEndingOffset() == offset - 1)
							{
								switch (previousLexeme.getType())
								{
									case CURLY_BRACE:
									case SEMICOLON:
										location = LocationType.INSIDE_PROPERTY;
										break;
										
									default:
										break;
								}
							}
							else
							{
								location = LocationType.INSIDE_PROPERTY;
							}
						}
					}
					break;

				case ELEMENT:
				case IDENTIFIER:
				case PROPERTY:
					if (index > 0)
					{
						Lexeme<CSSTokenType> previousLexeme = lexemeProvider.getLexeme(index - 1);
						
						if (previousLexeme.getType() == CSSTokenType.COLON)
						{
							this._replaceRange = this._currentLexeme = lexeme;
							location = LocationType.INSIDE_VALUE;
							break;
						}
					}
					
					if (lexeme.contains(offset) || lexeme.getEndingOffset() == offset - 1)
					{
						this._replaceRange = this._currentLexeme = lexeme;
					}
					else
					{
						this._replaceRange = this._currentLexeme = null;
					}
					location = LocationType.INSIDE_PROPERTY;
					break;
					
				case SEMICOLON:
					location = (lexeme.getEndingOffset() < offset) ? LocationType.INSIDE_PROPERTY : LocationType.INSIDE_VALUE;
					break;

				case COLON:
					location = (lexeme.getEndingOffset() < offset) ? LocationType.INSIDE_VALUE : LocationType.INSIDE_PROPERTY;
					break;
					
				case ARGS:
				case FUNCTION:
				case VALUE:
					location = LocationType.INSIDE_VALUE;
					break;

				default:
					break;
			}
			
			if (location != LocationType.ERROR)
			{
				break;
			}
			else
			{
				index--;
			}
		}

		return location;
	}

	/**
	 * getLexemeAfterDelimiter
	 * 
	 * @param lexemeProvider
	 * @param offset
	 * @return
	 */
	private int getLexemeAfterDelimiter(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		int index = lexemeProvider.getLexemeIndex(offset);
		
		if (index >= 0)
		{
			Lexeme<CSSTokenType> currentLexeme = lexemeProvider.getLexeme(index);
			
			if (currentLexeme.getType() == CSSTokenType.SEMICOLON)
			{
				index--;
				currentLexeme = lexemeProvider.getLexeme(index);
			}
	
			for (int i = index; i >= 0; i--)
			{
				Lexeme<CSSTokenType> previousLexeme = (i > 0) ? lexemeProvider.getLexeme(i - 1) : null;
	
				if (this.isValueDelimiter(currentLexeme))
				{
					index = i + 1;
					break;
				}
				else if (previousLexeme.isContiguousWith(currentLexeme) == false)
				{
					// there's a space between this lexeme and the previous lexeme
					// treat the previous lexeme like it is the delimiter
					index = i;
					break;
				}
				else
				{
					currentLexeme = previousLexeme;
					index = i;
				}
			}
		}
		
		return index;
	}

	/**
	 * getLexemeBeforeDelimiter
	 * 
	 * @param lexemeProvider
	 * @param index
	 * @return
	 */
	private Lexeme<CSSTokenType> getLexemeBeforeDelimiter(LexemeProvider<CSSTokenType> lexemeProvider, int index)
	{
		Lexeme<CSSTokenType> result = null;
		
		// get the staring lexeme
		Lexeme<CSSTokenType> startingLexeme = lexemeProvider.getLexeme(index);
		
		if (startingLexeme != null && this.isValueDelimiter(startingLexeme) == false)
		{
			Lexeme<CSSTokenType> endingLexeme = startingLexeme;
			
			// advance to next lexeme
			index++;
			
			while (index < lexemeProvider.size())
			{
				Lexeme<CSSTokenType> candidateLexeme = lexemeProvider.getLexeme(index);
				
				if (this.isValueDelimiter(candidateLexeme) || endingLexeme.isContiguousWith(candidateLexeme) == false)
				{
					// we've hit a delimiting lexeme or have passed over whitespace, so we're done
					break;
				}
				else
				{
					// still looking so include this in our range
					endingLexeme = candidateLexeme;
				}
				
				index++;
			}
			
			if (index >= lexemeProvider.size())
			{
				endingLexeme = lexemeProvider.getLexeme(lexemeProvider.size() - 1);
			}
			
			result = endingLexeme;
		}
		
		return result;
	}
	
	/**
	 * getLocation
	 * 
	 * @param lexemeProvider
	 * @param offset
	 * @return
	 */
	LocationType getCoarseLocationType(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		LocationType result = LocationType.ERROR;
		int index = lexemeProvider.getLexemeFloorIndex(offset);

		LOOP: while (index >= 0)
		{
			Lexeme<CSSTokenType> lexeme = lexemeProvider.getLexeme(index);

			switch (lexeme.getType())
			{
				case CURLY_BRACE:
					if ("{".equals(lexeme.getText())) //$NON-NLS-1$
					{
						if (lexeme.getEndingOffset() < offset)
						{
							result = LocationType.INSIDE_RULE;
							this._replaceRange = this._currentLexeme = null;
						}
						else
						{
							result = LocationType.OUTSIDE_RULE;
							this._replaceRange = this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset - 1);
						}
					}
					else
					{
						result = (lexeme.getEndingOffset() < offset) ? LocationType.OUTSIDE_RULE : LocationType.INSIDE_RULE;
					}
					break LOOP;

				case PROPERTY:
				case VALUE:
					result = LocationType.INSIDE_RULE;
					break LOOP;
					
				case IDENTIFIER:
					if (lexeme.getText().charAt(0) == '-')
					{
						result = LocationType.INSIDE_RULE;
						break LOOP;
					}
					break;

				default:
					break;
			}

			index--;
		}
		
		if (index < 0 && result == LocationType.ERROR)
		{
			result = LocationType.OUTSIDE_RULE;
		}

		return result;
	}

	/**
	 * getPropertyName
	 * 
	 * @param document
	 * @param offset
	 * @return
	 */
	private String getPropertyName(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		String result = null;
		int index = this.getIndexOfPreviousColon(lexemeProvider, offset);

		if (index > 0)
		{
			Lexeme<CSSTokenType> lexeme = lexemeProvider.getLexeme(index - 1);
			
			result = lexeme.getText();
		}

		return result;
	}

	/**
	 * isValueDelimiter
	 * 
	 * @param lexeme
	 * @return
	 */
	private boolean isValueDelimiter(Lexeme<CSSTokenType> lexeme)
	{
		boolean result = false;

		switch (lexeme.getType())
		{
			case COLON:
			case COMMA:
			case CURLY_BRACE:
			case SEMICOLON:
				result = true;
				break;

			default:
				result = false;
				break;
		}

		return result;
	}
	
	/**
	 * setPropertyValueRange
	 * 
	 * @param lexemeProvider
	 * @param offset
	 */
	private void setPropertyValueRange(LexemeProvider<CSSTokenType> lexemeProvider, int offset)
	{
		int index = this.getLexemeAfterDelimiter(lexemeProvider, offset);
		
		// get the staring lexeme
		Lexeme<CSSTokenType> endingLexeme = (index >= 0) ? this.getLexemeBeforeDelimiter(lexemeProvider, index) : null;
		
		if (endingLexeme != null)
		{
			Lexeme<CSSTokenType> startingLexeme = lexemeProvider.getLexeme(index);
		
			this._replaceRange = new Range(startingLexeme.getStartingOffset(), endingLexeme.getEndingOffset());
		}
		else
		{
			
			if (this._currentLexeme != null && (this._currentLexeme.contains(offset) || this._currentLexeme.getEndingOffset() == offset - 1))
			{
				switch (this._currentLexeme.getType())
				{
					case COLON:
						this._replaceRange = this._currentLexeme = null;
						break;
						
					case CURLY_BRACE:
						if ("}".equals(this._currentLexeme.getText()))
						{
							Lexeme<CSSTokenType> candidate = lexemeProvider.getLexemeFromOffset(offset - 1);
							
							if (this.isValueDelimiter(candidate) == false)
							{
								this._replaceRange = this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset - 1);
							}
							else
							{
								this._replaceRange = this._currentLexeme = null;
							}
						}
						else
						{
							this._replaceRange = this._currentLexeme = null;
						}
						break;
						
					case SEMICOLON:
						this._replaceRange = this._currentLexeme = lexemeProvider.getLexemeFromOffset(offset - 1);
						break;
						
					default:
						this._replaceRange = this._currentLexeme;
						break;
				}
			}
			else
			{
				this._replaceRange = this._currentLexeme = null;
			}
		}
	}
}
