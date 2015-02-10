/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.va.scmPublish;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link FolderCopy}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class FolderCopy
{

	/**
	 * Returns the copied file count
	 *  @param extensionsToSkip - case insensitive
	 */
	public static int copy(Path source, Path target, boolean includeSourceFolder, Set<String> extensionsToInclude) throws IOException
	{
		AtomicInteger copiedFileCount = new AtomicInteger(0);
		EnumSet<FileVisitOption> options = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		//check first if source is a directory
		if (Files.isDirectory(source))
		{
			target.toFile().mkdir();
			if (includeSourceFolder)
			{
				return copy(source, target.resolve(source.getFileName()), false, extensionsToInclude);
			}
			
			Files.walkFileTree(source, options, Integer.MAX_VALUE, new FileVisitor<Path>()
			{
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
				{
					CopyOption[] opt = new CopyOption[] { COPY_ATTRIBUTES, REPLACE_EXISTING };
					Path newDirectory = target.resolve(source.relativize(dir));
					try
					{
						Files.copy(dir, newDirectory, opt);
					}
					catch (FileAlreadyExistsException | DirectoryNotEmptyException x)
					{
						//don't care if the directory already existed
					}
					catch (IOException x)
					{
						throw x;
					}
					return CONTINUE;
				}
				
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
				{
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					boolean copy = false;
					if (extensionsToInclude == null || extensionsToInclude.size() == 0)
					{
						copy = true;
					}
					else
					{
						for (String s : extensionsToInclude)
						{
							if (file.toFile().getName().toLowerCase().endsWith(s.toLowerCase()))
							{
								copy = true;
								break;
							}
						}
					}
					if (copy)
					{
						CopyOption[] options = new CopyOption[] { REPLACE_EXISTING, COPY_ATTRIBUTES };
						Files.copy(file, target.resolve(source.relativize(file)), options);
						copiedFileCount.incrementAndGet();
					}
					return CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException
				{
					throw exc;
				}
			});
		}
		else
		{
			throw new IOException("Source is not a directory");
		}
		return copiedFileCount.get();
	}
	
	public static void main(String[] args) throws IOException
	{
		System.out.println(copy(new File("src").toPath(), new File("target").toPath(), true, null));
	}
}
