# sbt-hepek
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ba.sake/sbt-hepek/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ba.sake/sbt-hepek)  

Welcome to **sbt-hepek**, an [sbt](http://www.scala-sbt.org) plugin for rendering Scala `object`s to files.  
See also [**hepek**](https://github.com/sake92/hepek), static content generator that builds upon this plugin.

## Examples
- [my blog (full-blown example)](https://github.com/sake92/sake-ba-source) rendered @ [blog.sake.ba](https://blog.sake.ba)
- [examples](https://github.com/sake92/hepek-examples)
- ["Philosophy"](https://dev.to/sake_92/render-static-site-from-scala-code)

## Installing

Make sure you are using sbt 1.x!  
Add the following line to `project/plugins.sbt`:

```scala
addSbtPlugin("ba.sake" % "sbt-hepek" % "0.3.0")
```

and enable it in your `build.sbt`: 

```scala
enablePlugins(HepekPlugin)
// logLevel in hepek := Level.Debug // enable to see which objects are rendered
```

## Using

The main task of sbt-hepek is `hepek`.  
When executed, it will find all Scala `object`s that:
- extend [`Renderable`](https://github.com/sake92/hepek-core/blob/master/src/main/java/ba/sake/hepek/core/Renderable.java) and
- are in the `files` package

and write them into the `hepekTarget` folder.  
Default value for `hepekTarget` is `"hepek_files"`.  


Example:

```scala
package files // mandatory !!

import java.nio.file.Paths
import ba.sake.hepek.core.Renderable

object RenderMe extends Renderable {

  override def render =
    "Some text" // arbitrary Scala code
  
  override def relPath = 
    Paths.get("renderme.txt")
}
```

When you run `hepek` task, you'll find the `hepek_files/renderme.txt` file,  
with text `Some text`.

---

## Fun fact
I think that this is the first project that tried this approach, namely, using first-class Scala `object`s for this kind of stuff.  
Correct me if I'm wrong... ^_^

---


## About the name

A "hepek" in Bosnian language is a jargon for a thing/thingy/stuff...  
It is used when we don't know the name of a thing: "Give me that ... *hepek*".  
Also, it is used in the famous show called "Top lista nadrealista" as a name for an advanced device which calms down situations of various kinds.  

[![IMAGE ALT TEXT HERE](http://img.youtube.com/vi/Jc9SeKu-YwQ/0.jpg)](https://youtu.be/Jc9SeKu-YwQ?t=2m11s)